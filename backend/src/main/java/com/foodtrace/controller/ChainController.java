package com.foodtrace.controller;

import org.fisco.bcos.sdk.v3.client.Client;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 区块链链路自检控制器
 *
 * <p>提供读链（区块高度）检测接口，验证后端与节点的连通性。
 *
 * @author Microft0629
 * @since 2026-09-05
 */
@RestController
@RequestMapping("/api/chain")
public class ChainController {
    /** 链上客户端 */
    private final Client client;

    /**
     * 组装自检控制器
     *
     * @param client 群组客户端
     */
    public ChainController(Client client) {
        this.client = client;
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
}
