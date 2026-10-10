package com.foodtrace.service;

import com.foodtrace.chain.ChainHealthIndicator;
import com.foodtrace.chain.SignHealthIndicator;
import com.foodtrace.storage.StorageHealthIndicator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Status;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 健康状态指标采集
 *
 * <p>将各组件健康判定结果以 Gauge 发布到 /actuator/prometheus，
 * 供 Prometheus 抓取并触发告警（actuator 自身不把组件健康导出为指标）。
 *
 * @author Microft0629
 * @since 2026-10-10
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HealthGaugeTask {
    /** 链节点健康指示器 */
    private final ChainHealthIndicator chainHealth;
    /** 签名服务健康指示器 */
    private final SignHealthIndicator signHealth;
    /** 存证存储健康指示器 */
    private final StorageHealthIndicator storageHealth;
    /** 指标注册表 */
    private final MeterRegistry meterRegistry;
    /** 每个组件的 Gauge 持有值 */
    private final Map<String, AtomicInteger> gaugeCache = new ConcurrentHashMap<>();

    /**
     * 定期采集各组件健康状态并发布 Gauge（1 = 正常，0 = 异常）
     */
    @Scheduled(fixedRate = 15_000)
    public void collect() {
        set("chain", chainHealth.health().getStatus() == Status.UP ? 1 : 0);
        set("sign", signHealth.health().getStatus() == Status.UP ? 1 : 0);
        set("storage", storageHealth.health().getStatus() == Status.UP ? 1 : 0);
    }

    /**
     * 发布或更新一个组件健康 Gauge
     *
     * @param component 组件名
     * @param value     当前值
     */
    private void set(String component, int value) {
        gaugeCache.computeIfAbsent(component,
                k -> meterRegistry.gauge("app.health",
                        Tags.of(Tag.of("component", component)),
                        new AtomicInteger(0), AtomicInteger::doubleValue))
                .set(value);
    }
}
