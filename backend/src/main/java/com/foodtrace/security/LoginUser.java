package com.foodtrace.security;

/**
 * 已认证账户的请求身份
 * @param id           账户 id
 * @param username     登录名
 * @param regulator    是否监管
 * @param signUserId   链上操作的签名者 ID
 * @param chainAddress 链上地址
 * @author Microft0629
 * @since 2026-09-16
 */
public record LoginUser (Long id, String username, boolean regulator,
                         String signUserId, String chainAddress){

}
