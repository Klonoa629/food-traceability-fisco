package com.foodtrace.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求
 *
 * @param username 登录名
 * @param password 登录密码
 * @author Microft0629
 * @since 2026-09-17
 */
public record LoginRequest(
        @NotBlank(message = "用户名不能为空")
        String username,

        @NotBlank(message = "密码不能为空")
        String password) {
}
