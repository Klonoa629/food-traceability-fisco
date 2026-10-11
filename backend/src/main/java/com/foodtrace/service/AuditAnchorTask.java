package com.foodtrace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodtrace.chain.ChainAnchorService;
import com.foodtrace.dto.AuditChainVO;
import com.foodtrace.entity.SysUser;
import com.foodtrace.entity.OperateLog;
import com.foodtrace.mapper.OperateLogMapper;
import com.foodtrace.mapper.SysUserMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 审计链头锚定定时任务
 *
 * <p>周期性把审计哈希链的链头哈希写入链上锚定合约，
 * 校验锚定时的历史前缀，允许锚定之后正常追加日志。
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
    /** 审计哈希链服务 */
    private final AuditChainService auditChainService;
    /** 指标注册表 */
    private final MeterRegistry meterRegistry;

    /**
     * 锚定入口，周期由 foodtrace.anchor-interval-ms 配置（默认 10 分钟）
     */
    @Scheduled(fixedDelayString = "${foodtrace.anchor-interval-ms:600000}")
    public void anchor() {
        if (!enabled()) {
            return;
        }
        List<OperateLog> rows = snapshot();
        if (rows.isEmpty()) {
            return;
        }
        long rowCount = rows.size();
        AuditChainVO integrity = auditChainService.verifyRows(rows);
        if (!integrity.intact()) {
            log.warn("审计锚定跳过：{}", integrity.reason());
            meterRegistry.counter("audit.anchor.error").increment();
            return;
        }
        String headHash = rows.get(rows.size() - 1).getRowHash();
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
     * 核验最新锚定对应的历史前缀，正常追加不影响结果
     *
     * @return 一致返回 null，不一致返回差异描述
     */
    public boolean enabled() {
        return chainAnchorService.configured();
    }

    public String verifyAnchor() {
        if (!enabled()) {
            return "尚未配置审计锚定合约";
        }
        ChainAnchorService.AnchorRecord latest = chainAnchorService.getLatestAnchor();
        if (latest == null) {
            return "链上无锚定记录";
        }
        List<OperateLog> rows = snapshot();
        long anchoredCount = latest.rowCount();
        if (anchoredCount <= 0 || anchoredCount > rows.size()) {
            return "锚定行数异常：锚定时 " + anchoredCount + " 行，当前 " + rows.size() + " 行";
        }
        List<OperateLog> prefix = rows.subList(0, (int) anchoredCount);
        AuditChainVO integrity = auditChainService.verifyRows(prefix);
        if (!integrity.intact()) {
            return "已锚定日志不完整：" + integrity.reason();
        }
        String headHash = prefix.get(prefix.size() - 1).getRowHash();
        if (!headHash.equals(latest.headHash())) {
            return "已锚定日志的链头不一致（锚定时 " + anchoredCount + " 行）";
        }
        return null;
    }

    /**
     * 单次查询获取有序快照，行数与链头来自同一批数据
     */
    private List<OperateLog> snapshot() {
        return operateLogMapper.selectList(new LambdaQueryWrapper<OperateLog>()
                .orderByAsc(OperateLog::getId));
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
