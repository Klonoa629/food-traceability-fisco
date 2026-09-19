package com.foodtrace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 产品交接请求
 *
 * @param nextHolder 下游机构的链上地址
 * @author Microft0629
 * @since 2026-09-18
 */
public record HandoverRequest(
        @NotBlank(message = "下游机构地址不能为空")
        @Pattern(regexp = "^0x[0-9a-fA-F]{40}$", message = "下游机构地址格式不正确")
        String nextHolder) {
}
