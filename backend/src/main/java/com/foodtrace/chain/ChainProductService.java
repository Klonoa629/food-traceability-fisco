package com.foodtrace.chain;

import com.foodtrace.common.BizException;
import com.foodtrace.common.ErrorCode;
import com.foodtrace.config.ContractProperties;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.List;

/**
 * 链上产品业务交易封装
 *
 * <p>对接 Foodtrace 合约的产品注册、环节记录、交接、质检与召回交易，
 * 回执状态非 0 时抛出业务异常并携带合约 revert 信息。
 *
 * @author Microft0629
 * @since 2026-09-18
 */
@Component
public class ChainProductService {
    /** 上链写入统一入口 */
    private final ChainWriter chainWriter;
    /** Foodtrace 合约地址 */
    private final String contractAddress;
    /** Foodtrace 合约 ABI */
    private final String abi;

    /**
     * 组装链上产品交易组件
     *
     * @param chainWriter        上链写入入口
     * @param contractProperties 合约连接配置
     * @param abiHolder          ABI 资源持有者
     */
    public ChainProductService(ChainWriter chainWriter,
                               ContractProperties contractProperties, AbiHolder abiHolder) {
        this.chainWriter = chainWriter;
        this.contractAddress = contractProperties.getContractAddress();
        this.abi = abiHolder.get();
    }

    /**
     * 注册产品（基地写入首条种植记录）
     *
     * @param signUserId  签名用户标识
     * @param name        产品名称
     * @param batchNo     批次号
     * @param description 种植描述
     * @param location    产地
     * @param dataHash    数据哈希
     * @return 交易哈希
     * @throws BizException 交易未成功上链时抛出
     */
    public String registerProduct(String signUserId, String name, String batchNo,
                                   String description, String location, String dataHash) {
        return send(signUserId, "registerProduct",
                List.of(name, batchNo, description, location, dataHash), "注册产品");
    }

    /**
     * 添加环节记录
     *
     * @param signUserId  签名用户标识
     * @param productId   产品 id
     * @param stage       环节 0-5
     * @param description 记录描述
     * @param location    位置
     * @param dataHash    数据哈希
     * @return 交易哈希
     * @throws BizException 交易未成功上链时抛出
     */
    public String addRecord(String signUserId, long productId, int stage,
                            String description, String location, String dataHash) {
        return send(signUserId, "addRecord",
                List.of(BigInteger.valueOf(productId), BigInteger.valueOf(stage),
                        description, location, dataHash), "添加环节记录");
    }

    /**
     * 产品交接
     *
     * @param signUserId  签名用户标识
     * @param productId   产品 id
     * @param nextHolder 下游机构链上地址
     * @return 交易哈希
     * @throws BizException 交易未成功上链时抛出
     */
    public String handOver(String signUserId, long productId, String nextHolder) {
        return send(signUserId, "handOver",
                List.of(BigInteger.valueOf(productId), nextHolder), "产品交接");
    }

    /**
     * 质检裁决
     *
     * @param signUserId  签名用户标识
     * @param productId   产品 id
     * @param reportHash  质检报告哈希
     * @param qualified   是否合格
     * @return 交易哈希
     * @throws BizException 交易未成功上链时抛出
     */
    public String inspectProduct(String signUserId, long productId,
                                 String reportHash, boolean qualified) {
        return send(signUserId, "inspectProduct",
                List.of(BigInteger.valueOf(productId), reportHash, qualified), "产品质检");
    }

    /**
     * 产品召回（监管）
     *
     * @param signUserId 签名用户标识
     * @param productId  产品 id
     * @param reasonHash 召回原因哈希
     * @return 交易哈希
     * @throws BizException 交易未成功上链时抛出
     */
    public String recallProduct(String signUserId, long productId, String reasonHash) {
        return send(signUserId, "recallProduct",
                List.of(BigInteger.valueOf(productId), reasonHash), "产品召回");
    }

    /**
     * 发送产品交易并校验回执，失败时透出合约 revert 信息
     *
     * @param signUserId 签名用户标识
     * @param method     合约方法名
     * @param args       方法实参列表
     * @param action     失败提示中的操作名
     * @return 交易哈希
     * @throws BizException 无回执或回执状态非 0 时抛出
     */
    private String send(String signUserId, String method, List<Object> args, String action) {
        TransactionReceipt receipt = chainWriter.send(signUserId, contractAddress, abi, method, args);
        // SDK 的回执状态是 int 型，0 表示成功；失败时还原 revert 具体原因
        if (receipt == null || receipt.getStatus() != 0) {
            throw new BizException(ErrorCode.CHAIN_REJECTED,
                    action + "被链上拒绝：" + RevertReason.describe(receipt));
        }
        return receipt.getTransactionHash();
    }
}
