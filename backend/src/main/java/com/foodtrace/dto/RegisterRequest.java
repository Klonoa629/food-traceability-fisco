package com.foodtrace.dto;

import jakarta.validation.constraints.*;

/**
 * 注册请求
 *
 * @param username 登录名（3-32 位字母、数字或下划线）
 * @param password 登录密码
 * @param orgName  机构名称
 * @param role     申请的合约角色 1-6
 * @author Microft0629
 * @since 2026-09-17
 */
public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "^[A-Za-z0-9_]{3,32}$", message = "用户名仅限 3-32 位字母、数字或下划线")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度需在 8-64 位之间")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "密码须同时包含字母和数字")
        String password,

        @NotBlank(message = "机构名称不能为空")
        @Size(max = 128, message = "机构名称过长")
        String orgName,

        @NotNull(message = "申请角色不能为空")
        @Min(value = 1, message = "角色取值为 1-6")
        @Max(value = 6, message = "角色取值为 1-6")
        Integer role) {
}
