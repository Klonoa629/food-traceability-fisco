package com.foodtrace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 产品召回请求
 *
 * @param reason     召回原因
 * @param reasonHash 原因哈希（可空，空则由后端对原因文本计算 SHA-256）
 * @author Microft0629
 * @since 2026-09-18
 */
public record RecallRequest(
        @NotBlank(message = "召回原因不能为空")
        @Size(max = 512, message = "召回原因过长")
        String reason,

        @Size(max = 128, message = "原因哈希过长")
        String reasonHash) {
}
