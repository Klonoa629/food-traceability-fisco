package com.foodtrace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodtrace.config.ContractProperties;
import com.foodtrace.entity.OperateLog;
import com.foodtrace.mapper.OperateLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.sdk.v3.client.Client;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 操作审计服务
 *
 * <p>业务动作落 operate_log 表；审计写入失败只记日志，不阻断业务流程。
 *
 * @author Microft0629
 * @since 2026-09-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperateLogService {
    /** 审计 Mapper */
    private final OperateLogMapper operateLogMapper;
    /** 链客户端 */
    private final Client client;
    /** 合约连接配置 */
    private final ContractProperties contractProperties;

    /**
     * 记录一条操作审计
     *
     * @param userId      操作账户 id
     * @param username    操作账户名
     * @param action      具体操作
     * @param targetId    被操作对象 id（可空）
     * @param chainTxHash 链上交易哈希（可空）
     * @param detail      补充说明（可空）
     */
    public void record(Long userId, String username, String action,
                       Long targetId, String chainTxHash, String detail) {
        try {
            OperateLog logEntry = new OperateLog();
            logEntry.setUserId(userId);
            logEntry.setUsername(username);
            logEntry.setAction(action);
            logEntry.setTargetId(targetId);
            logEntry.setChainTxHash(chainTxHash);
            logEntry.setDetail(detail);
            operateLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.error("审计写入失败 action={} userId={}", action, userId, e);
        }
    }

    /**
     * 分页无关的条件查询审计记录
     *
     * @param action 操作类型（可空）
     * @param userId 操作账户 id（可空）
     * @return 按时间倒序的审计记录列表
     */
    public List<OperateLog> list(String action, Long userId) {
        LambdaQueryWrapper<OperateLog> wrapper = new LambdaQueryWrapper<>();
        if (action != null && !action.isBlank()) {
            wrapper.eq(OperateLog::getAction, action);
        }
        if (userId != null) {
            wrapper.eq(OperateLog::getUserId, userId);
        }
        wrapper.orderByDesc(OperateLog::getId);
        return operateLogMapper.selectList(wrapper);
    }

    /**
     * 链上校验所有带交易哈希的审计记录：交易须存在且指向溯源合约
     *
     * @return 审计记录 id -> 是否与链上一致（false 说明数据库记录疑似被篡改）
     */
    public Map<Long, Boolean> verifyOnChain() {
        Map<Long, Boolean> result = new LinkedHashMap<>();
        List<OperateLog> logs = operateLogMapper.selectList(
                new LambdaQueryWrapper<OperateLog>().isNotNull(OperateLog::getChainTxHash));
        for (OperateLog entry : logs) {
            result.put(entry.getId(), isOnChain(entry.getChainTxHash()));
        }
        return result;
    }

    /**
     * 校验单笔交易哈希：链上可查且接收方为溯源合约
     */
    private boolean isOnChain(String txHash) {
        try {
            var tx = client.getTransaction(txHash, false).getTransaction().orElse(null);
            return tx != null && tx.getTo() != null
                    && tx.getTo().equalsIgnoreCase(contractProperties.getContractAddress());
        } catch (Exception e) {
            return false;
        }
    }
}
