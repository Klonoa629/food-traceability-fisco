package com.foodtrace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodtrace.entity.ChainOperation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 写链操作 Mapper
 *
 * @author Microft0629
 * @since 2026-10-11
 */
@Mapper
public interface ChainOperationMapper extends BaseMapper<ChainOperation> {
    @Update("""
            UPDATE chain_operation SET attempts = attempts + 1, updated_at = CURRENT_TIMESTAMP
            WHERE operation_key = #{key}
            """)
    void attempted(@Param("key") String key);

    @Update("""
            UPDATE chain_operation SET status = #{status}, last_error = #{error},
                updated_at = CURRENT_TIMESTAMP WHERE operation_key = #{key}
            """)
    void outcome(@Param("key") String key, @Param("status") String status, @Param("error") String error);
}
