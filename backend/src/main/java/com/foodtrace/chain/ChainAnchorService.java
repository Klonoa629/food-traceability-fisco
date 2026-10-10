package com.foodtrace.chain;

import com.foodtrace.common.BizException;
import com.foodtrace.common.ErrorCode;
import org.fisco.bcos.sdk.v3.codec.ContractCodecException;
import org.fisco.bcos.sdk.v3.transaction.model.exception.TransactionBaseException;
import org.fisco.bcos.sdk.v3.BcosSDK;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.crypto.CryptoSuite;
import org.fisco.bcos.sdk.v3.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.v3.model.CryptoType;
import org.fisco.bcos.sdk.v3.transaction.manager.AssembleTransactionProcessor;
import org.fisco.bcos.sdk.v3.transaction.model.dto.CallResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.List;

/**
 * 审计链头锚定合约调用
 *
 * <p>将审计哈希链的链头定期写入链上独立合约，整库重写也无法伪造
 * 与锚一致的哈希链。
 *
 * @author Microft0629
 * @since 2026-10-10
 */
@Component
public class ChainAnchorService {
    /** 上链写入统一入口 */
    private final ChainWriter chainWriter;
    /** 交易组装器（只读查询） */
    private final AssembleTransactionProcessor assembler;
    /** 占位密钥对的链上地址 */
    private final String fromAddress;
    /** AuditAnchor 合约地址 */
    private final String contractAddress;
    /** AuditAnchor 合约 ABI */
    private final String abi;

    /**
     * 组装锚定合约客户端
     *
     * @param chainWriter        上链写入入口
     * @param bcosSDK            SDK 实例
     * @param client             群组客户端
     * @param groupId            群组 ID
     * @param anchorAddress      合约地址（由 foodtrace.anchor-address 配置）
     * @param abiHolder          ABI 资源持有者
     */
    public ChainAnchorService(ChainWriter chainWriter, BcosSDK bcosSDK, Client client,
                              @Value("${fisco.group-id}") String groupId,
                              @Value("${foodtrace.anchor-address:}") String anchorAddress,
                              AbiHolder abiHolder) {
        this.chainWriter = chainWriter;
        CryptoSuite cryptoSuite = new CryptoSuite(CryptoType.ECDSA_TYPE, bcosSDK.getConfig());
        CryptoKeyPair keyPair = cryptoSuite.generateRandomKeyPair();
        this.assembler = new AssembleTransactionProcessor(client, keyPair, groupId,
                client.getGroupInfo().getResult().getChainID(), "", "", "");
        this.fromAddress = keyPair.getAddress();
        this.contractAddress = anchorAddress;
        this.abi = loadAnchorAbi(abiHolder);
    }

    /**
     * 写入一次锚定（仅监管可调用）
     *
     * @param signUserId 监管签名用户标识
     * @param headHash   审计链头哈希
     * @param rowCount   锚定时审计行数
     * @return 交易哈希
     * @throws BizException 交易未成功上链时抛出
     */
    public String anchor(String signUserId, String headHash, long rowCount) {
        org.fisco.bcos.sdk.v3.model.TransactionReceipt receipt =
                chainWriter.send(signUserId, contractAddress, abi, "anchor",
                        List.of(headHash, BigInteger.valueOf(rowCount)));
        if (receipt == null || receipt.getStatus() != 0) {
            String detail = receipt == null ? "无回执" : receipt.getStatus() + " " + receipt.getMessage();
            throw new BizException(ErrorCode.CHAIN_REJECTED, "审计锚定上链失败：" + detail);
        }
        return receipt.getTransactionHash();
    }

    /**
     * 查询锚定记录总数
     *
     * @return 总数，合约未配置返回 -1
     */
    public long anchorCount() {
        if (contractAddress == null || contractAddress.isBlank()) {
            return -1;
        }
        Object result = call("anchorCount", List.of());
        return ((Number) result).longValue();
    }

    /**
     * 查询指定锚定记录
     *
     * @param id 锚定 id
     * @return 锚定记录，不存在返回 null
     */
    @SuppressWarnings("unchecked")
    public AnchorRecord getAnchor(long id) {
        if (contractAddress == null || contractAddress.isBlank()) {
            return null;
        }
        try {
            CallResponse resp = assembler.sendCall(fromAddress, contractAddress, abi,
                    "getAnchor", List.of(BigInteger.valueOf(id)));
            List<Object> returns = resp.getReturnObject();
            // getAnchor 返回 (string, uint256, uint256) 三个独立返回值
            if (returns == null || returns.size() < 3) {
                return null;
            }
            return new AnchorRecord(
                    String.valueOf(returns.get(0)),
                    ((Number) returns.get(1)).longValue(),
                    ((Number) returns.get(2)).longValue());
        } catch (TransactionBaseException | ContractCodecException e) {
            throw new IllegalStateException("锚定合约查询 getAnchor 失败：" + e.getMessage(), e);
        }
    }

    /**
     * 查询最新锚定记录
     *
     * @return 最新锚定，无记录或合约未配置返回 null
     */
    public AnchorRecord getLatestAnchor() {
        long count = anchorCount();
        if (count <= 0) {
            return null;
        }
        return getAnchor(count);
    }

    /**
     * 发起只读调用
     */
    private Object call(String method, List<Object> args) {
        try {
            CallResponse resp = assembler.sendCall(fromAddress, contractAddress, abi, method, args);
            return resp.getReturnObject().get(0);
        } catch (TransactionBaseException | ContractCodecException e) {
            throw new IllegalStateException("锚定合约查询 " + method + " 失败：" + e.getMessage(), e);
        }
    }

    /**
     * 加载锚定合约 ABI
     */
    private static String loadAnchorAbi(AbiHolder abiHolder) {
        String foodtraceAbi = abiHolder.get();
        // AuditAnchor.abi 已由资源加载器读取，此处按文件名约定取
        // 实际由 AbiHolder 扩展提供，暂从 classpath 直接读
        try (var is = ChainAnchorService.class.getClassLoader()
                .getResourceAsStream("abi/AuditAnchor.abi")) {
            if (is == null) {
                throw new IllegalStateException("缺少 abi/AuditAnchor.abi 资源");
            }
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("加载 AuditAnchor.abi 失败", e);
        }
    }

    /**
     * 锚定记录
     *
     * @param headHash  审计链头哈希
     * @param rowCount  审计时行数
     * @param timestamp 上链时间戳（毫秒）
     */
    public record AnchorRecord(String headHash, long rowCount, long timestamp) {
    }
}
