package com.foodtrace.chain;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * WeBASE-Sign 健康指示器
 *
 * <p>签名服务不可达意味着所有链上写操作停摆，纳入整体健康判定。
 *
 * @author Microft0629
 * @since 2026-10-09
 */
@Component
public class SignHealthIndicator implements HealthIndicator {
    /** WeBASE-Sign 客户端 */
    private final SignClient signClient;

    /**
     * 构造签名服务健康指示器
     *
     * @param signClient WeBASE-Sign 客户端
     */
    public SignHealthIndicator(SignClient signClient) {
        this.signClient = signClient;
    }

    /**
     * 探测签名服务连通性
     *
     * @return 可达为 UP，连接失败为 DOWN
     */
    @Override
    public Health health() {
        return signClient.ping() ? Health.up().build() : Health.down().build();
    }
}
