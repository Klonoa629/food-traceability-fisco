package com.foodtrace.common;

import lombok.Getter;

/**
 * 业务错误码
 *
 * @author Microft0629
 * @since 2026-09-10
 */
@Getter
public enum ErrorCode {
    /** 未登录或令牌无效 */
    UNAUTHORIZED(401, "未登录或令牌无效"),
    /** 无权访问 */
    FORBIDDEN(403, "无权访问"),
    /** 资源不存在 */
    NOT_FOUND(404, "资源不存在"),
    /** 状态不允许该操作 */
    INVALID_STATE(409, "状态不允许该操作"),
    /** 参数错误 */
    PARAM_ERROR(400, "参数错误"),
    /** 链上交易被拒绝 */
    CHAIN_REJECTED(502, "链上交易被拒绝");

    private final int code;
    private final String defaultMessage;

    /**
     * 构造错误码
     *
     * @param code           状态码
     * @param defaultMessage 默认信息
     */
    ErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }
}
