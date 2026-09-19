package com.foodtrace.controller;

import com.foodtrace.chain.ChainWriter;
import com.foodtrace.chain.RevertReason;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 区块链链路自检控制器
 *
 * <p>提供读链（区块高度）与写链（HelloWorld 合约 set）联调接口，验证方案 A 全链路。
 */
@RestController
@RequestMapping("/api/chain")
public class ChainController {
    private final Client client;
    private final ChainWriter chainWriter;

    public ChainController(Client client, ChainWriter chainWriter) {
        this.client = client;
        this.chainWriter = chainWriter;
    }

    /**
     * 查询链上最新区块高度
     *
     * @return 含 blockNumber 字段的响应体
     */
    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of("blockNumber", client.getBlockNumber().getBlockNumber());
    }
    /** HelloWorld 合约地址（测试用） */
    @Value("${webase.hello-address}")
    private String helloAddress;

    private static final String HELLO_ABI = "[{\"inputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"constructor\"},{\"inputs\":[],\"name\":\"get\",\"outputs\":[{\"internalType\":\"string\",\"name\":\"\",\"type\":\"string\"}],\"stateMutability\":\"view\",\"type\":\"function\"},{\"inputs\":[{\"internalType\":\"string\",\"name\":\"m\",\"type\":\"string\"}],\"name\":\"set\",\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}]";

    /**
     * 写链联调入口：调用 HelloWorld 合约的 set 方法将 value 上链
     *
     * @param body 请求体，含 signUserId（缺省 regulator_001）与 value
     * @return 交易哈希、回执状态与回执消息
     */
    @PostMapping("/test-write")
    public Map<String, Object> testWrite(@RequestBody Map<String, String> body) {
        TransactionReceipt receipt = chainWriter.send(
                body.getOrDefault("signUserId", "regulator_001"),
                helloAddress, HELLO_ABI, "set", List.of(body.get("value")));
        return Map.of("txHash", receipt.getTransactionHash(),
                "status", receipt.getStatus(),  // status=0成功
                // 失败时还原 revert 具体原因
                "message", receipt.getStatus() != 0
                        ? RevertReason.describe(receipt) : String.valueOf(receipt.getMessage()));
    }
}
