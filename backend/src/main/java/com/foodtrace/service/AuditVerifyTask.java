package com.foodtrace.service;

import com.foodtrace.dto.AuditChainVO;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 审计完整性定时校验
 *
 * <p>周期性运行哈希链校验与链上锚定比对，异常时记录告警日志与指标。
 * 前端按钮是按需手动触发，本任务保证无人值守时也能发现问题。
 *
 * @author Microft0629
 * @since 2026-10-10
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditVerifyTask {
    /** 审计哈希链服务 */
    private final AuditChainService auditChainService;
    /** 审计锚定任务 */
    private final AuditAnchorTask auditAnchorTask;
    /** 指标注册表 */
    private final MeterRegistry meterRegistry;

    /**
     * 定时校验入口，周期由 foodtrace.verify-interval-ms 配置（默认 10 分钟）
     */
    @Scheduled(fixedDelayString = "${foodtrace.verify-interval-ms:600000}")
    public void verify() {
        verifyChain();
        verifyAnchor();
    }

    /**
     * 校验审计哈希链完整性
     */
    private void verifyChain() {
        try {
            AuditChainVO result = auditChainService.verify();
            if (!result.intact()) {
                meterRegistry.counter("audit.verify.broken").increment();
                log.warn("审计链定时校验不通过：{}", result.reason());
            }
        } catch (Exception e) {
            log.warn("审计链定时校验异常：{}", e.getMessage());
        }
    }

    /**
     * 校验链上锚定与当前链头的一致性
     */
    private void verifyAnchor() {
        try {
            String mismatch = auditAnchorTask.verifyAnchor();
            if (mismatch != null) {
                meterRegistry.counter("audit.verify.anchor_mismatch").increment();
                log.warn("锚定定时校验不通过：{}", mismatch);
            }
        } catch (Exception e) {
            // 合约未配置或链不可达时静默跳过（锚定任务自身会记日志）
            log.debug("锚定校验跳过：{}", e.getMessage());
        }
    }
}
