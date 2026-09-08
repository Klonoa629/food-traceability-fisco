package com.foodtrace.chain;

import com.foodtrace.config.ContractProperties;
import org.fisco.bcos.sdk.v3.codec.ContractCodecException;
import org.fisco.bcos.sdk.v3.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.v3.transaction.model.exception.TransactionBaseException;
import org.fisco.bcos.sdk.v3.BcosSDK;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.crypto.CryptoSuite;
import org.fisco.bcos.sdk.v3.model.CryptoType;
import org.fisco.bcos.sdk.v3.transaction.manager.AssembleTransactionProcessor;
import org.fisco.bcos.sdk.v3.transaction.model.dto.CallResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 链上只读查询封装
 *
 * <p>基于 sendCall 发起 view 调用：不签名、不消耗资源、由节点本地执行。
 *
 * @author Microft0629
 * @since 2026-09-08
 */
@Component
public class ChainReader {
    /** 交易组装器 */
    private final AssembleTransactionProcessor assembler;
    /** 占位密钥对的链上地址 */
    private final String fromAddress;
    /** Foodtrace 合约地址 */
    private final String contractAddress;
    /** Foodtrace 合约 ABI */
    private final String abi;

    /**
     * 组装只读查询组件
     *
     * @param bcosSDK            SDK 实例
     * @param client             群组客户端
     * @param groupId            群组 ID
     * @param contractProperties 合约连接配置
     * @param abiHolder          ABI 资源持有者
     */
    public ChainReader(BcosSDK bcosSDK, Client client,
                       @Value("${fisco.group-id}") String groupId,
                       ContractProperties contractProperties, AbiHolder abiHolder) {
        CryptoSuite cryptoSuite = new CryptoSuite(CryptoType.ECDSA_TYPE, bcosSDK.getConfig());
        // 占位密钥对：view 调用不签名，from 地址仅作占位
        CryptoKeyPair keyPair = cryptoSuite.generateRandomKeyPair();
        this.assembler = new AssembleTransactionProcessor(client, keyPair, groupId,
                client.getGroupInfo().getResult().getChainID(), "", "", "");
        this.fromAddress = keyPair.getAddress();
        this.contractAddress = contractProperties.getContractAddress();
        this.abi = abiHolder.get();
    }

    /**
     * 查询机构的链上角色
     *
     * @param chainAddress 机构链上地址
     * @return 合约 Role 枚举数值
     */
    public int roles(String chainAddress) {
        try {
            CallResponse resp = assembler.sendCall(fromAddress, contractAddress,
                    abi, "roles", List.of(chainAddress));
            return ((Number) resp.getReturnObject().get(0)).intValue();
        } catch (TransactionBaseException | ContractCodecException e) {
            throw new IllegalStateException("链上查询 roles 失败：" + e.getMessage(), e);
        }


    }
}
