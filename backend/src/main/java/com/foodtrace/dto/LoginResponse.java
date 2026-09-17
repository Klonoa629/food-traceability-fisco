package com.foodtrace.dto;

/**
 * 登录响应
 *
 * @param token JWT 令牌
 * @param user  账户信息
 * @author Microft0629
 * @since 2026-09-17
 */
public record LoginResponse(String token, UserInfo user) {
}
