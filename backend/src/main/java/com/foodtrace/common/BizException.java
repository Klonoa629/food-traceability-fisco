package com.foodtrace.common;

import lombok.Getter;

/**
 * 业务异常
 *
 * @author Microft0629
 * @since 2026-09-10
 */
@Getter
public class BizException extends RuntimeException{
    /** 业务错误码 */
    private final int code;

    /**
     * 以错误码为构造，返回默认信息
     *
     * @param errorCode 业务错误码
     */
    public BizException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.code = errorCode.getCode();
    }

    /**
     * 以错误码与自定义返回信息构造
     * @param errorCode 业务错误码
     * @param message   自定义信息
     */
    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }
}
