package com.foodtrace.chain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodtrace.common.BizException;
import com.foodtrace.common.ErrorCode;
import com.foodtrace.entity.ChainOperation;
import com.foodtrace.mapper.ChainOperationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 广播前保存签名交易，超时后按原哈希查回执或重发原交易
 *
 * @author Microft0629
 * @since 2026-10-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChainOperationJournal {
    private final ChainOperationMapper mapper;
    private final Client client;
    private final ObjectMapper json;

    public record SignedTransaction(String txHash, String signedTx) {}

    public TransactionReceipt execute(String signer, String contract, String method, List<Object> args,
                                      Supplier<SignedTransaction> prepare) {
        String requestId = requestId();
        String key = digest(List.of(client.getGroup(), signer, contract, method, requestId));
        String payloadHash = digest(args);
        ChainOperation operation = mapper.selectById(key);
        if (operation == null) {
            SignedTransaction signed = prepare.get();
            operation = new ChainOperation();
            operation.setOperationKey(key);
            operation.setRequestId(requestId);
            operation.setSignUserId(signer);
            operation.setMethod(method);
            operation.setPayloadHash(payloadHash);
            operation.setTxHash(signed.txHash());
            operation.setSignedTx(signed.signedTx());
            operation.setStatus("PENDING");
            operation.setAttempts(0);
            try {
                mapper.insert(operation); // 失败即中止，绝不先广播后记账
            } catch (DuplicateKeyException concurrentRequest) {
                operation = mapper.selectById(key);
            }
        }
        if (operation == null || !payloadHash.equals(operation.getPayloadHash())) {
            throw new BizException(ErrorCode.INVALID_STATE, "同一请求标识不能提交不同内容");
        }
        return resolve(operation);
    }

    /**
     * 先查原交易；无回执才重发已保存的签名数据，链上哈希保持不变
     */
    TransactionReceipt resolve(ChainOperation operation) {
        return resolve(operation, true);
    }

    private TransactionReceipt resolve(ChainOperation operation, boolean allowBroadcast) {
        try {
            var found = client.getTransactionReceipt(operation.getTxHash(), false);
            TransactionReceipt receipt = found == null ? null : found.getTransactionReceipt();
            if (receipt == null) {
                if (!allowBroadcast) {
                    throw new IllegalStateException("已达自动重发上限，继续查询回执等待人工核对");
                }
                mapper.attempted(operation.getOperationKey());
                var sent = client.sendTransaction(operation.getSignedTx(), false);
                receipt = sent == null ? null : sent.getTransactionReceipt();
            }
            if (receipt == null) {
                throw new IllegalStateException("尚未取得回执");
            }
            if (!operation.getTxHash().equalsIgnoreCase(receipt.getTransactionHash())) {
                throw new IllegalStateException("返回回执与原交易哈希不一致");
            }
            mapper.outcome(operation.getOperationKey(),
                    receipt.getStatus() == 0 ? "CONFIRMED" : "REJECTED", null);
            return receipt;
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            try {
                mapper.outcome(operation.getOperationKey(), "PENDING",
                        message.substring(0, Math.min(message.length(), 512)));
            } catch (Exception unavailable) {
                log.warn("写链结果暂未回填 tx={}", operation.getTxHash());
            }
            throw new BizException(ErrorCode.CHAIN_PENDING,
                    "交易结果待确认，请保持内容不变重试；原交易 " + operation.getTxHash());
        }
    }

    /**
     * 后台核验待确认交易。自动广播最多尝试 10 次，之后保留待确认记录供人工核对。
     */
    @Scheduled(fixedDelayString = "${foodtrace.operation-reconcile-ms:60000}", initialDelay = 60000)
    public void reconcile() {
        List<ChainOperation> pending = mapper.selectList(new LambdaQueryWrapper<ChainOperation>()
                .eq(ChainOperation::getStatus, "PENDING")
                .orderByAsc(ChainOperation::getUpdatedAt).last("LIMIT 50"));
        for (ChainOperation operation : pending) {
            try {
                resolve(operation, operation.getAttempts() < 10);
            } catch (BizException e) {
                log.warn("写链结果仍待确认 tx={}", operation.getTxHash());
            }
        }
    }

    /** 重启或后台重发后仍能识别本服务交易，避免误报为外部写入 */
    public boolean isKnownTransaction(String txHash) {
        return mapper.selectCount(new LambdaQueryWrapper<ChainOperation>()
                .eq(ChainOperation::getTxHash, txHash)) > 0;
    }

    private String requestId() {
        var context = RequestContextHolder.getRequestAttributes();
        if (context instanceof ServletRequestAttributes servlet) {
            String id = servlet.getRequest().getHeader("Idempotency-Key");
            if (id == null || !id.matches("[A-Za-z0-9_-]{16,80}")) {
                throw new BizException(ErrorCode.PARAM_ERROR, "写链请求须携带 16-80 位 Idempotency-Key");
            }
            return id;
        }
        return UUID.randomUUID().toString(); // 定时锚定每轮是独立操作
    }

    private String digest(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json.writeValueAsString(value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("计算操作摘要失败", e);
        }
    }
}
