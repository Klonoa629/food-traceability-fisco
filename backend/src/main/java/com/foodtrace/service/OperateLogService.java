package com.foodtrace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodtrace.config.ContractProperties;
import com.foodtrace.entity.OperateLog;
import com.foodtrace.entity.SysUser;
import com.foodtrace.mapper.OperateLogMapper;
import com.foodtrace.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.crypto.CryptoSuite;
import org.fisco.bcos.sdk.v3.model.CryptoType;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.fisco.bcos.sdk.v3.utils.Hex;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
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
    /** 动作 -> 期望的合约事件签名（首个索引参数为产品 id 或机构地址） */
    private static final Map<String, String> EVENT_OF_ACTION = Map.of(
            "APPROVE_USER", "RoleUpdated(address,uint8)",
            "REVOKE_USER", "RoleUpdated(address,uint8)",
            "REGISTER_PRODUCT", "ProductRegistered(uint256,string,address)",
            "ADD_RECORD", "RecordAdded(uint256,uint8,address)",
            "HANDOVER", "ProductHandedOver(uint256,address,address)",
            "INSPECT", "ProductInspected(uint256,bool)",
            "RECALL", "ProductRecalled(uint256,address)");

    /** 审计 Mapper */
    private final OperateLogMapper operateLogMapper;
    /** 账户 Mapper */
    private final SysUserMapper userMapper;
    /** 链客户端 */
    private final Client client;
    /** 合约连接配置 */
    private final ContractProperties contractProperties;
    /** keccak256 哈希，用于计算事件主题 */
    private final CryptoSuite cryptoSuite = new CryptoSuite(CryptoType.ECDSA_TYPE);

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
     * 链上校验所有带交易哈希的审计记录
     *
     * <p>校验口径：交易回执成功，且由溯源合约发出了该动作对应的业务事件，
     * 事件首个索引参数与审计对象一致——产品动作比对 product_id 与 target_id，
     * 审批/吊销比对机构地址与该账户的链上地址。仅凭别笔合法交易哈希
     * 无法通过校验。
     *
     * @return 审计记录 id -> 是否与链上一致（false 说明数据库记录疑似被篡改）
     */
    public Map<Long, Boolean> verifyOnChain() {
        Map<Long, Boolean> result = new LinkedHashMap<>();
        List<OperateLog> logs = operateLogMapper.selectList(
                new LambdaQueryWrapper<OperateLog>().isNotNull(OperateLog::getChainTxHash));
        for (OperateLog entry : logs) {
            result.put(entry.getId(), verifyEntry(entry));
        }
        return result;
    }

    /**
     * 校验单条审计记录与链上回执是否吻合
     *
     * @param entry 审计记录
     * @return 吻合返回 true，无法确认或不一致返回 false
     */
    private boolean verifyEntry(OperateLog entry) {
        String eventSignature = EVENT_OF_ACTION.get(entry.getAction());
        String expectedTopic1 = expectedIndexedValue(entry, eventSignature);
        if (eventSignature == null || expectedTopic1 == null) {
            return false;
        }
        try {
            TransactionReceipt receipt = client
                    .getTransactionReceipt(entry.getChainTxHash(), false).getTransactionReceipt();
            if (receipt == null || receipt.getStatus() != 0) {
                return false;
            }
            String topic0 = Hex.toHexStringWithPrefix(cryptoSuite.hash(
                    eventSignature.getBytes(StandardCharsets.UTF_8)));
            for (TransactionReceipt.Logs eventLog : receipt.getLogEntries()) {
                List<String> topics = eventLog.getTopics();
                // 链上地址/主题可能不带 0x 前缀，统一按裸十六进制比较
                if (topics.size() >= 2
                        && sameHex(contractProperties.getContractAddress(), eventLog.getAddress())
                        && sameHex(topic0, topics.get(0))
                        && sameHex(expectedTopic1, topics.get(1))) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 比较两个十六进制值是否相同（忽略 0x 前缀与大小写）
     *
     * @param a 十六进制值一
     * @param b 十六进制值二
     * @return 相同返回 true
     */
    private static boolean sameHex(String a, String b) {
        return a != null && b != null
                && Hex.trimPrefix(a).equalsIgnoreCase(Hex.trimPrefix(b));
    }

    /**
     * 计算该审计动作期望的事件首个索引参数（32 字节十六进制）
     *
     * @param entry          审计记录
     * @param eventSignature 动作对应的事件签名（未知动作为 null）
     * @return 期望值，无法确定时返回 null
     */
    private String expectedIndexedValue(OperateLog entry, String eventSignature) {
        if (eventSignature == null || entry.getTargetId() == null) {
            return null;
        }
        // 审批/吊销的对象是账户，比对链上地址；其余为产品动作，比对产品 id
        if (eventSignature.startsWith("RoleUpdated")) {
            SysUser user = userMapper.selectById(entry.getTargetId());
            String address = user == null ? null : user.getChainAddress();
            return address == null ? null
                    : "0x" + "0".repeat(24) + Hex.trimPrefix(address).toLowerCase();
        }
        return String.format("0x%064x", entry.getTargetId());
    }
}
