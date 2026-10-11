package com.foodtrace.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 质检裁决请求
 *
 * @param qualified  质检是否合格
 * @param reportHash 已上传报告的文件引用（file:sha256: 加 64 位内容哈希）
 * @author Microft0629
 * @since 2026-09-18
 */
public record InspectRequest(
        @NotNull(message = "质检结论不能为空")
        Boolean qualified,

        @NotBlank(message = "请先上传检测报告文件")
        @Pattern(regexp = "file:sha256:[0-9a-fA-F]{64}", message = "报告文件引用格式不正确")
        String reportHash) {
}
