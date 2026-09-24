package com.foodtrace.config;

import com.foodtrace.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置
 *
 * <p>无状态 JWT 认证：登录与注册开放，监管接口要求 REGULATOR 角色，
 * 其余接口要求已认证。
 *
 * @author Microft0629
 * @since 2026-09-16
 */
@Configuration
public class SecurityConfig {

    /**
     * 安全过滤链
     *
     * @param http      安全配置器
     * @param jwtFilter JWT 认证过滤器
     * @return 过滤链
     * @throws Exception 配置失败时抛出
     */
    @Bean
    SecurityFilterChain chain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        http.csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        // 放行错误页：sendError 的 ERROR 分发会重入过滤链，不放行会把 403 覆盖成 401
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/register").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("REGULATOR")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        // 未认证返回 401，无权限返回 403
                        .authenticationEntryPoint((req, res, ex) -> res.sendError(401))
                        .accessDeniedHandler((req, res, ex) -> res.sendError(403)))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 密码编码器
     *
     * @return BCrypt 编码器
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
