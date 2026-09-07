package com.foodtrace.chain;

import org.fisco.bcos.sdk.v3.BcosSDK;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.client.protocol.response.BcosTransactionReceipt;
import org.fisco.bcos.sdk.v3.crypto.CryptoSuite;
import org.fisco.bcos.sdk.v3.crypto.signature.ECDSASignatureResult;
import org.fisco.bcos.sdk.v3.model.CryptoType;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.fisco.bcos.sdk.v3.transaction.codec.encode.TransactionEncoderService;
import org.fisco.bcos.sdk.v3.transaction.manager.AssembleTransactionProcessor;
import org.fisco.bcos.sdk.v3.utils.Hex;
import org.fisco.bcos.sdk.v3.utils.Numeric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

/* 上链写入入口：组装交易->Sign托管签名->广播->返回回执 */
@Component
public class ChainWriter {
    private static final Logger log = LoggerFactory.getLogger(ChainWriter.class);

    private final Client client;                           // 链上客户端
    private final CryptoSuite cryptoSuite;                 // 加密套件(哈希与交易编码)
    private final TransactionEncoderService encoder;       // 交易编码服务
    private final AssembleTransactionProcessor assembler;  // 交易组装器
    private final RemoteSignProvider signProvider;         // 远程签名上下文(向Sign传递signUserId)
    private final SignClient signClient;                   // WeBASE-Sign客户端

    public ChainWriter(BcosSDK bcosSDK, Client client, RemoteSignProvider signProvider,
                       SignClient signClient, @Value("${fisco.group-id}") String groupId) {
        this.client = client;
        this.cryptoSuite = new CryptoSuite(CryptoType.ECDSA_TYPE, bcosSDK.getConfig());
        this.encoder = new TransactionEncoderService(cryptoSuite);
        // 占位密钥对，满足组装器构造
        this.assembler = new AssembleTransactionProcessor(client,
                cryptoSuite.generateRandomKeyPair(), groupId,
                client.getGroupInfo().getResult().getChainID(), "", "", "");
        this.signProvider = signProvider;
        this.signClient = signClient;
    }

    // 写链统一入口
    public TransactionReceipt send(String signUserId, String contractAddress,
                                   String abi, String method, List<Object> args) {
        signProvider.setCurrentUser(signUserId);
        try {
            // 组装交易
            long handle = assembler.getRawTransaction(contractAddress, abi, method, args);
            // 取未签名交易的交易哈希，交由 Sign 托管身份签名
            byte[] hash = encoder.encodeAndHashBytes(handle);
            String sigHex = signClient.signMessageHash(signUserId, Hex.toHexStringWithPrefix(hash));
            // 解析签名
            byte[] sig = Numeric.hexStringToByteArray(sigHex);
            ECDSASignatureResult signature = new ECDSASignatureResult(sig[0],
                    Arrays.copyOfRange(sig, 1, 33), Arrays.copyOfRange(sig, 33, 65));
            // 回填签名得到最终交易，广播并等待回执
            byte[] signedTx = encoder.encodeToTransactionBytes(
                    handle, signature, cryptoSuite.getCryptoTypeConfig());
            BcosTransactionReceipt bcosReceipt =
                    client.sendTransaction(Hex.toHexStringWithPrefix(signedTx), false);
            TransactionReceipt receipt = bcosReceipt.getTransactionReceipt();
            if (receipt == null) {
                throw new IllegalStateException("交易未上链");
            }
            log.info("上链完成 txHash={} status={}",
                    receipt.getTransactionHash(), receipt.getStatus());
            return receipt;
        } catch (Exception e) {
            throw new IllegalStateException("写链失败: " + e.getMessage(), e);
        } finally {
            signProvider.clearCurrentUser();
        }
    }
}