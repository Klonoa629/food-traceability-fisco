package com.foodtrace.chain;

import com.foodtrace.config.ContractProperties;
import com.foodtrace.service.OperateLogService;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.crypto.CryptoSuite;
import org.fisco.bcos.sdk.v3.eventsub.EventSubscribe;
import org.fisco.bcos.sdk.v3.eventsub.EventSubParams;
import org.fisco.bcos.sdk.v3.model.CryptoType;
import org.fisco.bcos.sdk.v3.model.EventLog;
import org.fisco.bcos.sdk.v3.utils.Hex;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 合约事件订阅
 *
 * <p>后台订阅 Foodtrace 合约事件：外部（未经后端）的链上操作即时失效
 * 产品缓存并落 CHAIN_EVENT 审计；自有交易只做缓存失效兜底，审计仍由
 * 业务路径携带完整上下文记录。
 *
 * @author Microft0629
 * @since 2026-10-10
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "foodtrace.event-subscribe", havingValue = "true", matchIfMissing = true)
public class ChainEventSubscriber {
    /** 事件主题（裸十六进制小写）-> 事件名 */
    private static final Map<String, String> EVENT_OF_TOPIC;

    static {
        CryptoSuite suite = new CryptoSuite(CryptoType.ECDSA_TYPE);
        Map<String, String> mapping = new HashMap<>();
        for (String signature : List.of(
                "RoleUpdated(address,uint8)",
                "RegulatorChanged(address,address)",
                "ProductRegistered(uint256,string,address)",
                "RecordAdded(uint256,uint8,address)",
                "ProductHandedOver(uint256,address,address)",
                "ProductInspected(uint256,bool)",
                "ProductRecalled(uint256,address)")) {
            String topic = Hex.toHexStringWithPrefix(
                    suite.hash(signature.getBytes(StandardCharsets.UTF_8)));
            mapping.put(Hex.trimPrefix(topic).toLowerCase(), signature.split("\\(")[0]);
        }
        EVENT_OF_TOPIC = Map.copyOf(mapping);
    }

    /** 链客户端 */
    private final Client client;
    /** 合约连接配置 */
    private final ContractProperties contractProperties;
    /** 产品读缓存 */
    private final ProductCache productCache;
    /** 审计服务 */
    private final OperateLogService operateLogService;
    /** 上链写入统一入口（区分内外部交易） */
    private final ChainWriter chainWriter;
    /** 指标注册表 */
    private final MeterRegistry meterRegistry;
    /** 重订阅调度 */
    private final ScheduledExecutorService retry =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "event-sub-retry");
                t.setDaemon(true);
                return t;
            });
    /** 防止重复排定重订阅 */
    private final AtomicBoolean retryScheduled = new AtomicBoolean(false);
    /** 订阅标识 */
    private volatile String subscribeId;

    /**
     * 启动时建立订阅（仅订阅新产生的事件，历史由链上校验覆盖）
     */
    @PostConstruct
    void start() {
        try {
            EventSubParams params = new EventSubParams();
            params.setFromBlock(BigInteger.valueOf(-1));
            params.setToBlock(BigInteger.valueOf(-1));
            params.addAddress(contractProperties.getContractAddress());
            subscribeId = EventSubscribe.build(client).subscribeEvent(params, this::onReceiveLog);
            retryScheduled.set(false);
            log.info("合约事件订阅已建立 id={} 合约={}", subscribeId,
                    contractProperties.getContractAddress());
        } catch (Exception e) {
            log.warn("合约事件订阅建立失败：{}，30 秒后重试", e.getMessage());
            scheduleRetry();
        }
    }

    /**
     * 停止订阅与调度线程
     */
    @PreDestroy
    void stop() {
        retry.shutdownNow();
        String id = subscribeId;
        if (id != null) {
            try {
                EventSubscribe.build(client).unsubscribeEvent(id);
            } catch (Exception ignored) {
                // 进程退出时节点不可达无需处理
            }
        }
    }

    /**
     * 订阅回调：状态非 0 视为异常并安排重订阅
     *
     * @param recordId 订阅记录标识
     * @param status   推送状态，0 为正常
     * @param logs     本次推送的事件日志
     */
    void onReceiveLog(String recordId, int status, List<EventLog> logs) {
        if (status != 0) {
            log.warn("合约事件订阅状态异常 status={}，30 秒后重订阅", status);
            meterRegistry.counter("chain.event.error").increment();
            scheduleRetry();
            return;
        }
        for (EventLog eventLog : logs) {
            try {
                handleLog(eventLog);
            } catch (Exception e) {
                log.warn("事件处理失败 tx={}：{}", eventLog.getTransactionHash(), e.getMessage());
            }
        }
    }

    /**
     * 处理单条事件：失效缓存；外部交易额外落审计
     *
     * @param eventLog 事件日志
     */
    void handleLog(EventLog eventLog) {
        List<String> topics = eventLog.getTopics();
        String topic0 = topics.isEmpty() ? "" : Hex.trimPrefix(topics.get(0)).toLowerCase();
        String event = EVENT_OF_TOPIC.getOrDefault(topic0, "Unknown");
        meterRegistry.counter("chain.event.received", "event", event).increment();

        Long productId = extractProductId(event, topics);
        if (productId != null) {
            productCache.evict(productId);
        }
        if (chainWriter.isOwn(eventLog.getTransactionHash())) {
            // 自有交易已由业务路径携带完整上下文审计
            return;
        }
        if (operateLogService.existsByTxHash(eventLog.getTransactionHash())) {
            // 历史交易已有审计（含重启前的自有交易），节点重放不重复记录
            return;
        }
        meterRegistry.counter("chain.event.external", "event", event).increment();
        log.warn("检测到外部链上交易：{} tx={}", event, eventLog.getTransactionHash());
        operateLogService.record(0L, "system", "CHAIN_EVENT", productId,
                eventLog.getTransactionHash(), "外部链上交易：" + event + "（未经后端的合约操作）");
    }

    /**
     * 从事件主题提取产品 id（产品类事件的首个索引参数）
     *
     * @param event  事件名
     * @param topics 事件主题
     * @return 产品 id，非产品事件返回 null
     */
    private static Long extractProductId(String event, List<String> topics) {
        boolean productEvent = event.startsWith("Product") || "RecordAdded".equals(event);
        if (!productEvent || topics.size() < 2) {
            return null;
        }
        return new BigInteger(1, Hex.decode(Hex.trimPrefix(topics.get(1)))).longValue();
    }

    /**
     * 安排一次重订阅（并发去重）
     */
    private void scheduleRetry() {
        if (retryScheduled.compareAndSet(false, true)) {
            retry.schedule(this::start, 30, TimeUnit.SECONDS);
        }
    }
}