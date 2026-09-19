package com.foodtrace.dto;

/**
 * 单条溯源记录（链上数据视图）
 *
 * @param stage      环节 0-5（6 = 召回记录）
 * @param description 描述
 * @param operator   操作机构地址
 * @param location   位置
 * @param dataHash   数据哈希
 * @param timestamp  上链时间戳（毫秒）
 * @author Microft0629
 * @since 2026-09-18
 */
public record TraceRecordVO(
        int stage,
        String description,
        String operator,
        String location,
        String dataHash,
        long timestamp) {
}
