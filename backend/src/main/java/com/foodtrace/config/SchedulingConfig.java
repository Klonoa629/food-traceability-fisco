package com.foodtrace.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务配置
 *
 * <p>开启 Spring 调度，支撑周期性对账任务。
 *
 * @author Microft0629
 * @since 2026-10-06
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
