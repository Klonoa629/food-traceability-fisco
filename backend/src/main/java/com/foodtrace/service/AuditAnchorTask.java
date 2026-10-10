package com.foodtrace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodtrace.chain.ChainAnchorService;
import com.foodtrace.entity.SysUser;
import com.foodtrace.entity.OperateLog;
import com.foodtrace.mapper.OperateLogMapper;
import com.foodtrace.mapper.SysUserMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 审计链头锚定定时任务
 *
 * <p>周期性把审计哈希链的链头哈希写入链上锚定合约，
 * 整库重写无法伪造与锚一致的链条。锚定本身就是一条审计记录，
 * 因此锚定行自身也进入哈希链（由 record 接链）。
 *
 * @author Microft0629
 * @since 2026-10-10
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditAnchorTask {
    /** 审计 Mapper */
    private final OperateLogMapper operateLogMapper;
    /** 账户 Mapper */
    private final SysUserMapper userMapper;
    /** 锚定合约客户端 */
    private final ChainAnchorService chainAnchorService;
    /** 审计服务 */
    private final OperateLogService operateLogService;
    /** 指标注册表 */
    private final MeterRegistry meterRegistry;

    /**
     * 锚定入口，周期由 foodtrace.anchor-interval-ms 配置（默认 10 分钟）
     */
    @Scheduled(fixedDelayString = "${foodtrace.anchor-interval-ms:600000}")
    public void anchor() {
        String headHash = currentHeadHash();
        if (headHash == null) {
            return;
        }
        long rowCount = operateLogMapper.selectCount(new LambdaQueryWrapper<>());
        String signUserId = regulatorSignUserId();
        if (signUserId == null) {
            log.warn("审计锚定跳过：未找到生效的监管账户");
            return;
        }
        try {
            String txHash = chainAnchorService.anchor(signUserId, headHash, rowCount);
            meterRegistry.counter("audit.anchor.written").increment();
            log.info("审计链头已锚定：head={} rows={} tx={}",
                    headHash.substring(0, 16) + "…", rowCount, txHash);
        } catch (Exception e) {
            meterRegistry.counter("audit.anchor.error").increment();
            log.warn("审计锚定失败：{}", e.getMessage());
        }
    }

    /**
     * 验证当前审计链头与最新锚定是否一致
     *
     * @return 一致返回 null（无异常），不一致返回差异描述
     */
    public String verifyAnchor() {
        ChainAnchorService.AnchorRecord latest = chainAnchorService.getLatestAnchor();
        if (latest == null) {
            return "链上无锚定记录";
        }
        String headHash = currentHeadHash();
        if (headHash == null) {
            return "审计表为空但链上有锚定记录（行数 " + latest.rowCount() + "）";
        }
        long rowCount = operateLogMapper.selectCount(new LambdaQueryWrapper<>());
        if (!latest.headHash().equals(headHash)) {
            return String.format("链头不一致：锚定=%s… 当前=%s… （锚定时 %d 行，当前 %d 行）",
                    latest.headHash().substring(0, 16),
                    headHash.substring(0, 16),
                    latest.rowCount(), rowCount);
        }
        return null;
    }

    /**
     * 获取当前审计链头（最新已接链行的 row_hash）
     */
    private String currentHeadHash() {
        OperateLog head = operateLogMapper.selectOne(new LambdaQueryWrapper<OperateLog>()
                .isNotNull(OperateLog::getRowHash)
                .orderByDesc(OperateLog::getId)
                .last("LIMIT 1"));
        return head == null ? null : head.getRowHash();
    }

    /**
     * 获取生效监管账户的签名用户标识
     */
    private String regulatorSignUserId() {
        SysUser regulator = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getIsRegulator, true)
                .eq(SysUser::getStatus, 1)
                .isNotNull(SysUser::getSignUserId)
                .last("LIMIT 1"));
        return regulator == null ? null : regulator.getSignUserId();
    }
}
