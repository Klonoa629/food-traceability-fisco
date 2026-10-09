package com.foodtrace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodtrace.dto.AuditChainVO;
import com.foodtrace.entity.OperateLog;
import com.foodtrace.mapper.OperateLogMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 审计哈希链服务
 *
 * <p>每行审计记录 prev_hash 指向上一行的 row_hash，row_hash 为本行全部
 * 业务字段的规范化摘要。任一行被删除、修改或插入伪造行，重算链条即可检出。
 * 首行的 prev_hash 为全零创世值；存量行在应用启动时自动回填。
 *
 * @author Microft0629
 * @since 2026-10-09
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditChainService implements ApplicationRunner {
    /** 创世前向哈希（全零） */
    public static final String GENESIS = "0".repeat(64);
    /** 时间格式（与 DATETIME 秒级精度一致，保证落库往返不变） */
    private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** 审计 Mapper */
    private final OperateLogMapper operateLogMapper;
    /** 指标注册表 */
    private final MeterRegistry meterRegistry;

    /**
     * 启动时回填未接链的行，并顺带校验已接链部分
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        List<OperateLog> rows = operateLogMapper.selectList(
                new LambdaQueryWrapper<OperateLog>().orderByAsc(OperateLog::getId));
        String prev = GENESIS;
        int backfilled = 0;
        for (OperateLog row : rows) {
            if (row.getRowHash() == null) {
                row.setPrevHash(prev);
                row.setRowHash(hash(prev, row));
                operateLogMapper.updateById(row);
                backfilled++;
            } else if (!hash(prev, row).equals(row.getRowHash())) {
                log.warn("启动校验发现审计链不完整，首处断点 id={}，共 {} 行", row.getId(), rows.size());
                meterRegistry.counter("audit.chain.broken").increment();
                break;
            }
            prev = row.getRowHash();
        }
        if (backfilled > 0) {
            log.info("审计哈希链回填完成：{} 行", backfilled);
        }
    }

    /**
     * 全量校验审计链完整性
     *
     * @return 校验结果，完整时 firstBrokenId 为 null
     */
    public AuditChainVO verify() {
        List<OperateLog> rows = operateLogMapper.selectList(
                new LambdaQueryWrapper<OperateLog>().orderByAsc(OperateLog::getId));
        AuditChainVO result = verifyRows(rows);
        if (!result.intact()) {
            meterRegistry.counter("audit.chain.broken").increment();
            log.warn("审计链校验不通过：{}", result.reason());
        }
        return result;
    }

    /**
     * 对有序行集合做链完整性校验
     *
     * @param rows 按 id 升序的审计行
     * @return 校验结果
     */
    AuditChainVO verifyRows(List<OperateLog> rows) {
        String prev = GENESIS;
        for (OperateLog row : rows) {
            if (row.getRowHash() == null || row.getPrevHash() == null) {
                return broken(row.getId(), "id=" + row.getId() + " 未接入哈希链");
            }
            if (!prev.equals(row.getPrevHash())) {
                return broken(row.getId(), "id=" + row.getId() + " 的前向哈希与上一行不符，疑似删行");
            }
            if (!hash(prev, row).equals(row.getRowHash())) {
                return broken(row.getId(), "id=" + row.getId() + " 的内容哈希不匹配，疑似篡改");
            }
            prev = row.getRowHash();
        }
        return new AuditChainVO(true, rows.size(), null, null);
    }

    /**
     * 计算一行的内容哈希
     *
     * @param prevHash 前一行哈希（创世为全零）
     * @param row      审计行
     * @return 64 位十六进制 SHA-256
     */
    public String hash(String prevHash, OperateLog row) {
        return sha256(canonical(prevHash, row));
    }

    /**
     * 构造规范化串：长度前缀拼接，杜绝分隔符歧义
     *
     * @param prevHash 前一行哈希
     * @param row      审计行
     * @return 待哈希的规范化文本
     */
    private static String canonical(String prevHash, OperateLog row) {
        return field(prevHash)
                + field(String.valueOf(row.getUserId()))
                + field(row.getUsername())
                + field(row.getAction())
                + field(row.getTargetId() == null ? "" : String.valueOf(row.getTargetId()))
                + field(row.getChainTxHash() == null ? "" : row.getChainTxHash())
                + field(row.getDetail() == null ? "" : row.getDetail())
                + field(row.getCreatedAt() == null ? "" : row.getCreatedAt().format(TIME));
    }

    /**
     * 单字段的长度前缀编码
     */
    private static String field(String value) {
        return value.length() + ":" + value;
    }

    /**
     * SHA-256 摘要
     *
     * @param content 原文
     * @return 小写十六进制
     */
    private static String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /**
     * 构造不完整结果
     */
    private static AuditChainVO broken(long id, String reason) {
        return new AuditChainVO(false, 0, id, reason);
    }
}
