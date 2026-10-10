package com.foodtrace.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 对象存储配置
 *
 * @author Microft0629
 * @since 2026-10-10
 */
@Data
@Component
@ConfigurationProperties(prefix = "foodtrace.storage")
public class StorageProperties {
    /** MinIO 服务地址 */
    private String endpoint = "http://127.0.0.1:9000";
    /** 访问密钥 */
    private String accessKey = "minioadmin";
    /** 秘密密钥 */
    private String secretKey = "minioadmin";
    /** 存证桶名 */
    private String bucket = "foodtrace-evidence";
}
