package com.foodtrace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodtrace.entity.OperateLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作审计 Mapper
 *
 * @author Microft0629
 * @since 2026-09-09
 */
@Mapper
public interface OperateLogMapper extends BaseMapper<OperateLog> {
}
