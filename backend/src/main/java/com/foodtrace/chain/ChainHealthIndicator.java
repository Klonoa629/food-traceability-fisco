package com.foodtrace.chain;

import org.fisco.bcos.sdk.v3.client.Client;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 链节点健康指示器
 *
 * <p>取最新区块高度验证与节点的连通性。
 *
 * @author Microft0629
 * @since 2026-10-09
 */
@Component
public class ChainHealthIndicator implements HealthIndicator {
    /** 链上客户端 */
    private final Client client;

    /**
     * 构造链健康指示器
     *
     * @param client 群组客户端
     */
    public ChainHealthIndicator(Client client) {
        this.client = client;
    }

    /**
     * 探测链节点
     *
     * @return 含区块号的健康信息，失败为 DOWN
     */
    @Override
    public Health health() {
        try {
            long blockNumber = client.getBlockNumber().getBlockNumber().longValue();
            return Health.up().withDetail("blockNumber", blockNumber).build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
