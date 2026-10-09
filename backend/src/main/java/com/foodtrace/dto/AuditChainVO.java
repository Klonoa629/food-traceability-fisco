package com.foodtrace.dto;

/**
 * 审计哈希链校验结果
 *
 * @param intact        链是否完整
 * @param total         参与校验的行数（仅完整时有意义）
 * @param firstBrokenId 首处断点行 id，完整时为 null
 * @param reason        断点原因描述
 * @author Microft0629
 * @since 2026-10-09
 */
public record AuditChainVO(boolean intact, long total, Long firstBrokenId, String reason) {
}
