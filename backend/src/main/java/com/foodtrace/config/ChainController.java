package com.foodtrace.config;

import org.fisco.bcos.sdk.v3.client.Client;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/* REST控制器：查询并返回区块链最新区块高度 */
@RestController
@RequestMapping("/api/chain")
public class ChainController {
    private final Client client;
    public ChainController(Client client) {
        this.client = client;
    }

    // 查看最新区块高度
    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of("blockNumber", client.getBlockNumber().getBlockNumber());
    }
}
