package com.foodtrace.config;

import com.foodtrace.chain.ChainWriter;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/* REST控制器：查询并返回区块链最新区块高度 */
@RestController
@RequestMapping("/api/chain")
public class ChainController {
    private final Client client;
    private final ChainWriter chainWriter;

    public ChainController(Client client, ChainWriter chainWriter) {
        this.client = client;
        this.chainWriter = chainWriter;
    }

    // 查看最新区块高度
    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of("blockNumber", client.getBlockNumber().getBlockNumber());
    }

    @Value("${webase.hello-address}")
    private String helloAddress;

    private static final String HELLO_ABI = "[{\"inputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"constructor\"},{\"inputs\":[],\"name\":\"get\",\"outputs\":[{\"internalType\":\"string\",\"name\":\"\",\"type\":\"string\"}],\"stateMutability\":\"view\",\"type\":\"function\"},{\"inputs\":[{\"internalType\":\"string\",\"name\":\"m\",\"type\":\"string\"}],\"name\":\"set\",\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}]";

    // 测试写链：调Hello合约的set，把value存上链
    @PostMapping("/test-write")
    public Map<String, Object> testWrite(@RequestBody Map<String, String> body) {
        TransactionReceipt receipt = chainWriter.send(
                body.getOrDefault("signUserId", "regulator_001"),
                helloAddress, HELLO_ABI, "set", List.of(body.get("value")));
        return Map.of("txHash", receipt.getTransactionHash(),
                "status", receipt.getStatus(),  // status=0成功
                "message", String.valueOf(receipt.getMessage()));
    }
}
