package com.foodtrace.chain;

import com.foodtrace.config.ContractProperties;
import com.foodtrace.dto.ProductVO;
import com.foodtrace.dto.TraceRecordVO;
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

import java.math.BigInteger;
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
        return ((Number) callValue("roles", List.of(chainAddress))).intValue();
    }

    /**
     * 查询已注册产品总数
     *
     * @return 产品总数
     */
    public long productCount() {
        return ((Number) callValue("getProductCount", List.of())).longValue();
    }

    /**
     * 按产品 id 查询完整信息
     *
     * @param productId 产品 id
     * @return 产品视图
     */
    public ProductVO product(long productId) {
        return parseProduct(callValue("getProduct", List.of(BigInteger.valueOf(productId))));
    }

    /**
     * 按批次号查询完整信息
     *
     * @param batchNo 批次号
     * @return 产品视图
     */
    public ProductVO productByBatch(String batchNo) {
        return parseProduct(callValue("getProductByBatch", List.of(batchNo)));
    }

    /**
     * 发起 view 调用并取第一个返回值
     *
     * @param method 合约方法名
     * @param args   方法实参列表
     * @return 解码后的首个返回对象
     * @throws IllegalStateException 链上调用失败时抛出
     */
    private Object callValue(String method, List<Object> args) {
        try {
            CallResponse resp = assembler.sendCall(fromAddress, contractAddress, abi, method, args);
            return resp.getReturnObject().get(0);
        } catch (TransactionBaseException | ContractCodecException e) {
            throw new IllegalStateException("链上查询 " + method + " 失败：" + e.getMessage(), e);
        }
    }

    /**
     * 将 ABI 解码的产品结构转为视图对象。
     * SDK 对 struct 可能返回按下标排列的 List，也可能返回按字段名的 Map，两种都兼容。
     *
     * @param decoded 解码后的产品结构
     * @return 产品视图
     */
    private static ProductVO parseProduct(Object decoded) {
        List<TraceRecordVO> records = new ArrayList<>();
        Object rawRecords = field(decoded, 7, "records");
        for (Object raw : asTuple(rawRecords)) {
            records.add(new TraceRecordVO(
                    (int) longOf(field(raw, 0, "stage")),
                    strOf(field(raw, 1, "description")),
                    strOf(field(raw, 2, "operator")),
                    strOf(field(raw, 3, "location")),
                    strOf(field(raw, 4, "data_hash")),
                    longOf(field(raw, 5, "timestamp"))));
        }
        return new ProductVO(
                longOf(field(decoded, 0, "id")),
                strOf(field(decoded, 1, "name")),
                strOf(field(decoded, 2, "batch_no")),
                strOf(field(decoded, 3, "origin_farm")),
                strOf(field(decoded, 4, "current_holder")),
                (int) longOf(field(decoded, 5, "stage")),
                booleanOf(field(decoded, 6, "recalled")),
                records);
    }

    /**
     * 按 List 下标或 Map 字段名取结构成员
     */
    private static Object field(Object tuple, int index, String name) {
        if (tuple instanceof List<?> list) {
            return list.get(index);
        }
        if (tuple instanceof Map<?, ?> map) {
            return map.get(name);
        }
        throw new IllegalStateException("链上产品结构解析失败：" + tuple);
    }

    /**
     * 将数组/结构成员规整为集合
     */
    private static List<?> asTuple(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        throw new IllegalStateException("链上记录数组解析失败：" + value);
    }

    private static long longOf(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static String strOf(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean booleanOf(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
