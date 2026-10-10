package com.foodtrace.storage;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 存证存储健康指示器
 *
 * @author Microft0629
 * @since 2026-10-10
 */
@Component
public class StorageHealthIndicator implements HealthIndicator {
    /** 存证存储服务 */
    private final StorageService storageService;

    /**
     * 构造存储健康指示器
     *
     * @param storageService 存证存储服务
     */
    public StorageHealthIndicator(StorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * 探测存证存储
     *
     * @return 可达为 UP
     */
    @Override
    public Health health() {
        return storageService.healthy() ? Health.up().build() : Health.down().build();
    }
}
