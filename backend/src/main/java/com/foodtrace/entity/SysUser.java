package com.foodtrace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 平台账户实体（表 sys_user）
 *
 * @author Microft0629
 * @since 2026-09-09
 */
@Data
@TableName("sys_user")
public class SysUser {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 登录名 */
    private String username;
    /** 密码哈希 */
    private String passwordHash;
    /** 密码版本（改密递增，使旧令牌失效） */
    private Integer pwdVersion;
    /** 机构名称 */
    private String orgName;
    /** 合约角色 0-6 */
    private Integer role;
    /** 是否监管账户 */
    private Boolean isRegulator;
    /** 链上账户地址（审批时回填） */
    private String chainAddress;
    /** 签名标识 */
    private String signUserId;
    /** 账户状态：0 = 待审批，1 = 生效，2 = 已吊销 */
    private Integer status;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新时间 */
    private LocalDateTime updatedAt;
}
