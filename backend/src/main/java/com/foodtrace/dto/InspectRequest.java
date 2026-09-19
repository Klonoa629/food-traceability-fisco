package com.foodtrace.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 质检裁决请求
 *
 * @param qualified  质检是否合格
 * @param reportHash 质检报告哈希（可空，空则由后端对裁决内容计算 SHA-256）
 * @author Microft0629
 * @since 2026-09-18
 */
public record InspectRequest(
        @NotNull(message = "质检结论不能为空")
        Boolean qualified,

        @Size(max = 128, message = "报告哈希过长")
        String reportHash) {
}
