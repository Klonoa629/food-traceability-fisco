package com.foodtrace.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 合约事件进度：回放包含最后成功处理的区块，重复事件由审计交易哈希去重
 *
 * @author Microft0629
 * @since 2026-10-11
 */
@Mapper
public interface ChainEventCursorMapper {
    @Select("SELECT block_number FROM chain_event_cursor WHERE stream_key = #{key}")
    Long findBlock(@Param("key") String key);

    @Insert("""
            INSERT INTO chain_event_cursor (stream_key, block_number) VALUES (#{key}, #{block})
            ON DUPLICATE KEY UPDATE block_number = GREATEST(block_number, #{block})
            """)
    void advance(@Param("key") String key, @Param("block") long block);
}
