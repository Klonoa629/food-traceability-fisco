package com.foodtrace.dto;

import com.foodtrace.entity.SysUser;

import java.time.LocalDateTime;

/**
 * 账户信息（不含密码）
 *
 * @param id           账户 id
 * @param username     登录名
 * @param orgName      机构名称
 * @param role         合约角色 0-6
 * @param regulator    是否监管账户
 * @param chainAddress 链上账户地址
 * @param signUserId   签名标识
 * @param status       账户状态：0 = 待审批，1 = 生效，2 = 已吊销
 * @param createdAt    创建时间
 * @author Microft0629
 * @since 2026-09-17
 */
public record UserInfo(
        Long id,
        String username,
        String orgName,
        Integer role,
        boolean regulator,
        String chainAddress,
        String signUserId,
        Integer status,
        LocalDateTime createdAt) {

    /**
     * 由账户实体构造对外信息
     *
     * @param user 账户实体
     * @return 账户信息
     */
    public static UserInfo from(SysUser user) {
        return new UserInfo(user.getId(), user.getUsername(), user.getOrgName(),
                user.getRole(), Boolean.TRUE.equals(user.getIsRegulator()),
                user.getChainAddress(), user.getSignUserId(), user.getStatus(),
                user.getCreatedAt());
    }
}
