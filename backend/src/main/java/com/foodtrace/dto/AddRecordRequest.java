package com.foodtrace.dto;

import jakarta.validation.constraints.*;

/**
 * 环节记录请求
 *
 * @param stage      环节 0-5（0=种植 1=加工 2=质检 3=运输 4=仓储 5=销售）
 * @param description 记录描述
 * @param location   位置
 * @param dataHash   数据哈希（可空，空则由后端对内容计算 SHA-256）
 * @author Microft0629
 * @since 2026-09-18
 */
public record AddRecordRequest(
        @NotNull(message = "环节不能为空")
        @Min(value = 0, message = "环节取值为 0-5")
        @Max(value = 5, message = "环节取值为 0-5")
        Integer stage,

        @NotBlank(message = "记录描述不能为空")
        @Size(max = 512, message = "记录描述过长")
        String description,

        @NotBlank(message = "位置不能为空")
        @Size(max = 128, message = "位置过长")
        String location,

        @Size(max = 128, message = "数据哈希过长")
        String dataHash) {
}
