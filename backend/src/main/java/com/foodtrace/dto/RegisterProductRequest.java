package com.foodtrace.dto;

import jakarta.validation.constraints.*;

/**
 * 产品注册请求
 *
 * @param name        产品名称
 * @param batchNo     批次号（业务唯一标识）
 * @param description 首条种植记录描述
 * @param location    产地/位置
 * @param dataHash    数据哈希（可空，空则由后端对内容计算 SHA-256）
 * @author Microft0629
 * @since 2026-09-18
 */
public record RegisterProductRequest(
        @NotBlank(message = "产品名称不能为空")
        @Size(max = 128, message = "产品名称过长")
        String name,

        @NotBlank(message = "批次号不能为空")
        @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$", message = "批次号仅限 1-64 位字母、数字、下划线或连字符")
        String batchNo,

        @NotBlank(message = "产品描述不能为空")
        @Size(max = 512, message = "产品描述过长")
        String description,

        @NotBlank(message = "产地不能为空")
        @Size(max = 128, message = "产地过长")
        String location,

        @Size(max = 128, message = "数据哈希过长")
        String dataHash) {
}
