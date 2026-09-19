package com.foodtrace.chain;

import com.foodtrace.common.BizException;
import com.foodtrace.common.ErrorCode;
import com.foodtrace.config.ContractProperties;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.List;

/**
 * 链上机构角色管理
 *
 * <p>以监管机构签名身份调用 Foodtrace 合约的 setRole / removeRole，
 * 交易未成功上链时抛出业务异常。
 *
 * @author Microft0629
 * @since 2026-09-17
 */
@Component
public class ChainRoleService {
    /** 上链写入统一入口 */
    private final ChainWriter chainWriter;
    /** Foodtrace 合约地址 */
    private final String contractAddress;
    /** Foodtrace 合约 ABI */
    private final String abi;

    /**
     * 组装链上角色管理组件
     *
     * @param chainWriter        上链写入入口
     * @param contractProperties 合约连接配置
     * @param abiHolder          ABI 资源持有者
     */
    public ChainRoleService(ChainWriter chainWriter,
                            ContractProperties contractProperties, AbiHolder abiHolder) {
        this.chainWriter = chainWriter;
        this.contractAddress = contractProperties.getContractAddress();
        this.abi = abiHolder.get();
    }

    /**
     * 给机构发放链上角色
     *
     * @param regulatorSignUserId 监管机构的签名用户标识
     * @param chainAddress        机构链上地址
     * @param role                合约角色 1-6
     * @return 交易哈希
     * @throws BizException 交易未成功上链时抛出
     */
    public String grantRole(String regulatorSignUserId, String chainAddress, int role) {
        return send(regulatorSignUserId, "setRole",
                List.of(chainAddress, BigInteger.valueOf(role)), "发放链上角色");
    }

    /**
     * 移除机构链上角色
     *
     * @param regulatorSignUserId 监管机构的签名用户标识
     * @param chainAddress        机构链上地址
     * @return 交易哈希
     * @throws BizException 交易未成功上链时抛出
     */
    public String removeRole(String regulatorSignUserId, String chainAddress) {
        return send(regulatorSignUserId, "removeRole",
                List.of(chainAddress), "移除链上角色");
    }

    /**
     * 发送角色管理交易并校验回执
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
