package com.foodtrace.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 食品溯源合约连接配置
 *
 * @author Microft0629
 * @since 2026-09-08
 */
@Data
@Component
@ConfigurationProperties(prefix = "foodtrace")
public class ContractProperties {
    /** Foodtrace 合约地址 */
    private String contractAddress;
    /** JWT 签发配置 */
    private Jwt jwt = new Jwt();

    /**
     * JWT 配置项
     */
    @Data
    public static class Jwt {
        /** 签名密钥 */
        private String secret;
        /** 令牌有效期 */
        private long ttlMinutes = 120;
    }

}
