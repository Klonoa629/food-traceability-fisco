package com.foodtrace.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/* Spring Security配置 */
@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain chain(HttpSecurity http) throws Exception {
        // 关闭CSRF
        http.csrf(c -> c.disable())
                // “/api/**”全部放行
                .authorizeHttpRequests(a -> a.requestMatchers("/api/**").permitAll()
                                                                              .anyRequest().authenticated());
        return http.build();
    }
}
