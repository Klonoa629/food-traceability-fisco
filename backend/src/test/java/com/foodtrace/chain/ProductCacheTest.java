package com.foodtrace.chain;

import com.foodtrace.dto.ProductVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 链上产品读缓存测试：命中、过期回源、写后回填与失效
 *
 * @author Microft0629
 * @since 2026-10-07
 */
@ExtendWith(MockitoExtension.class)
class ProductCacheTest {
    @Mock
    private ChainReader chainReader;

    private final AtomicLong now = new AtomicLong(1_700_000_000_000L);
    private ProductCache cache;

    /**
     * 以可控时钟与 1 分钟存活期构造被测缓存
     */
    @BeforeEach
    void setUp() {
        cache = new ProductCache(chainReader, 60_000L, now::get);
    }

    /**
     * 构造产品视图
     */
    private ProductVO product(long id, String batchNo, int stage) {
        return new ProductVO(id, "草莓", batchNo, "0xf", "0xf", stage, false, List.of());
    }

    @Test
    void secondReadWithinTtlShouldHitCache() {
        when(chainReader.product(1L)).thenReturn(product(1L, "B1", 0));

        cache.get(1L);
        cache.get(1L);

        verify(chainReader, times(1)).product(1L);
    }

    @Test
    void expiredEntryShouldReloadFromChain() {
        when(chainReader.product(1L)).thenReturn(product(1L, "B1", 0));

        cache.get(1L);
        now.addAndGet(60_001L);
        cache.get(1L);

        verify(chainReader, times(2)).product(1L);
    }

    @Test
    void batchLookupShouldRouteThroughIdCache() {
        when(chainReader.productByBatch("B1")).thenReturn(product(1L, "B1", 0));

        cache.getByBatch("B1");
        cache.getByBatch("B1");

        verify(chainReader, times(1)).productByBatch("B1");
        verify(chainReader, never()).product(anyLong());
    }

    @Test
    void putShouldRefreshEntryWithoutChainCall() {
        cache.put(product(1L, "B1", 3));

        assertThat(cache.get(1L).stage()).isEqualTo(3);
        assertThat(cache.getByBatch("B1").stage()).isEqualTo(3);
        verifyNoInteractions(chainReader);
    }

    @Test
    void evictShouldForceReload() {
        when(chainReader.product(1L)).thenReturn(product(1L, "B1", 0));
        cache.get(1L);

        cache.evict(1L);
        cache.get(1L);

        verify(chainReader, times(2)).product(1L);
    }

    @Test
    void chainFailureShouldPropagate() {
        when(chainReader.product(anyLong())).thenThrow(new IllegalStateException("链不可达"));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, () -> cache.get(1L));
        verify(chainReader, never()).productByBatch(anyString());
    }
}
