package com.foodtrace.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * WeBASE-Sign 连接配置
 *
 * @author Microft0629
 * @since 2026-09-17
 */
@Data
@Component
@ConfigurationProperties(prefix = "webase")
public class WebaseProperties {
    /** WeBASE-Sign 服务基础地址 */
    private String signUrl;
    /** 托管用户归属的应用编号 */
    private String appId = "foodtrace";
}
