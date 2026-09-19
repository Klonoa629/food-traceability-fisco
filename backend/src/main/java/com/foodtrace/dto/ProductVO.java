package com.foodtrace.dto;

import java.util.List;

/**
 * 产品完整信息（链上数据视图）
 *
 * @param id            产品 id
 * @param name          产品名称
 * @param batchNo       批次号
 * @param originFarm    初始基地地址
 * @param currentHolder 当前责任方地址
 * @param stage         当前环节 0-5（6 = 已召回）
 * @param recalled      是否已召回
 * @param records       溯源记录（按上链时间排序）
 * @author Microft0629
 * @since 2026-09-18
 */
public record ProductVO(
        long id,
        String name,
        String batchNo,
        String originFarm,
        String currentHolder,
        int stage,
        boolean recalled,
        List<TraceRecordVO> records) {
}
