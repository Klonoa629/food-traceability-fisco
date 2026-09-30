package com.foodtrace.dto;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 * 分页结果
 *
 * @param records 当前页数据
 * @param total   总条数
 * @param page    当前页码（从 1 起）
 * @param size    每页条数
 * @param <T>     数据类型
 * @author Microft0629
 * @since 2026-09-30
 */
public record PageVO<T>(List<T> records, long total, long page, long size) {

    /**
     * 由 MyBatis-Plus 分页结果构造
     *
     * @param page 分页查询结果
     * @param <T>  数据类型
     * @return 分页视图
     */
    public static <T> PageVO<T> of(IPage<T> page) {
        return new PageVO<>(page.getRecords(), page.getTotal(),
                page.getCurrent(), page.getSize());
    }
}
