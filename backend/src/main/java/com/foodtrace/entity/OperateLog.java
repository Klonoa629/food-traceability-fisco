package com.foodtrace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作审计实体（表 operate_log）
 *
 * @author Microft0629
 * @since 2026-09-09
 */
@Data
@TableName("operate_log")
public class OperateLog {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 操作账户 id */
    private Long userId;
    /** 操作账户名 */
    private String username;
    /** 具体操作 */
    private String action;
    /** 被操作对象 id */
    private Long targetId;
    /** 链上交易哈希 */
    private String chainTxHash;
    /** 补充说明 */
    private String detail;
    /** 前一行哈希（防删改链） */
    private String prevHash;
    /** 本行内容哈希（防篡改） */
    private String rowHash;
    /** 创建时间 */
    private LocalDateTime createdAt;
}
