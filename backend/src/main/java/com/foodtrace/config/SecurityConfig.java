package com.foodtrace.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 配置
 *
 * <p>测试联调阶段放行全部 /api/** 请求，之后引入 JWT 认证后收紧。
 */
@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain chain(HttpSecurity http) throws Exception {
        // 关闭 CSRF
        http.csrf(c -> c.disable())
                // "/api/**" 全部放行
                .authorizeHttpRequests(a -> a.requestMatchers("/api/**").permitAll()
                                                                              .anyRequest().authenticated());
        return http.build();
    }
}
