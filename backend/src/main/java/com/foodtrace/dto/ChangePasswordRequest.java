package com.foodtrace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 修改密码请求
 *
 * @param oldPassword 当前密码
 * @param newPassword 新密码（8-64 位，至少含字母和数字）
 * @author Microft0629
 * @since 2026-10-10
 */
public record ChangePasswordRequest(
        @NotBlank(message = "当前密码不能为空")
        String oldPassword,

        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 64, message = "新密码长度需在 8-64 位之间")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "新密码须同时包含字母和数字")
        String newPassword) {
}
