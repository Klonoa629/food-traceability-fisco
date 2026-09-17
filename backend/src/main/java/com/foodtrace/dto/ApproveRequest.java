package com.foodtrace.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 账户审批请求
 *
 * @param role 审批通过的合约角色 1-6
 * @author Microft0629
 * @since 2026-09-17
 */
public record ApproveRequest(
        @NotNull(message = "审批角色不能为空")
        @Min(value = 1, message = "角色取值为 1-6")
        @Max(value = 6, message = "角色取值为 1-6")
        Integer role) {
}
