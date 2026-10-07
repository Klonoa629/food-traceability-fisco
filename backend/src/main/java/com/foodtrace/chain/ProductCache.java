package com.foodtrace.chain;

import com.foodtrace.dto.ProductVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 链上产品读缓存
 *
 * <p>按产品 id 缓存链上查询结果，带存活时间兜底（链数据只增不删，
 * 但记录会追加，写路径须主动失效或回填）；批次号维护二级索引。
 *
 * @author Microft0629
 * @since 2026-10-07
 */
@Component
public class ProductCache {
    /** 链上只读查询 */
    private final ChainReader chainReader;
    /** 缓存存活时间（毫秒） */
    private final long ttlMs;
    /** 当前时间毫秒源（可注入便于测试） */
    private final LongSupplier clock;
    /** 产品 id -> 缓存条目 */
    private final Map<Long, Entry> byId = new ConcurrentHashMap<>();
    /** 批次号 -> 产品 id */
    private final Map<String, Long> batchToId = new ConcurrentHashMap<>();

    /**
     * 以配置的存活时间构造
     *
     * @param chainReader 链上只读查询
     * @param ttlMs       缓存存活时间，由 foodtrace.product-cache-ttl-ms 配置
     */
    @Autowired
    public ProductCache(ChainReader chainReader,
                        @Value("${foodtrace.product-cache-ttl-ms:60000}") long ttlMs) {
        this(chainReader, ttlMs, System::currentTimeMillis);
    }

    /**
     * 以指定时钟构造（测试用）
     *
     * @param chainReader 链上只读查询
     * @param ttlMs       缓存存活时间（毫秒）
     * @param clock       时间毫秒源
     */
    ProductCache(ChainReader chainReader, long ttlMs, LongSupplier clock) {
        this.chainReader = chainReader;
        this.ttlMs = ttlMs;
        this.clock = clock;
    }

    /**
     * 按产品 id 查询，优先取缓存，过期或未缓存时回源链上
     *
     * @param productId 产品 id
     * @return 产品视图
     * @throws IllegalStateException 链上查询失败时抛出
     */
    public ProductVO get(long productId) {
        Entry entry = byId.get(productId);
        if (entry != null && clock.getAsLong() - entry.loadedAt() < ttlMs) {
            return entry.product();
        }
        ProductVO fresh = chainReader.product(productId);
        byId.put(productId, new Entry(fresh, clock.getAsLong()));
        return fresh;
    }

    /**
     * 按批次号查询，已解析过批次时直接走 id 缓存
     *
     * @param batchNo 批次号
     * @return 产品视图
     * @throws IllegalStateException 链上查询失败时抛出
     */
    public ProductVO getByBatch(String batchNo) {
        Long id = batchToId.get(batchNo);
        if (id != null) {
            return get(id);
        }
        ProductVO fresh = chainReader.productByBatch(batchNo);
        batchToId.put(batchNo, fresh.id());
        byId.put(fresh.id(), new Entry(fresh, clock.getAsLong()));
        return fresh;
    }

    /**
     * 写路径回读后回填缓存
     *
     * @param product 链上最新产品视图
     */
    public void put(ProductVO product) {
        byId.put(product.id(), new Entry(product, clock.getAsLong()));
        batchToId.put(product.batchNo(), product.id());
    }

    /**
     * 失效单个产品的缓存，下次读取回源
     *
     * @param productId 产品 id
     */
    public void evict(long productId) {
        byId.remove(productId);
    }

    /**
     * 缓存条目
     *
     * @param product  产品视图
     * @param loadedAt 加载时刻毫秒
     */
    private record Entry(ProductVO product, long loadedAt) {
    }
}
