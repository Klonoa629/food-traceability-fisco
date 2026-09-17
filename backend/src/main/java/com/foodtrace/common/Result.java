package com.foodtrace.common;

import lombok.Data;

/**
 * 接口统一返回格式
 *
 * @author Microft0629
 * @since 2026-09-09
 */
@Data
public class Result<T> {
    /** 业务状态码：0 表示成功 */
    private int code;
    /** 提示信息 */
    private String message;
    /** 业务数据 */
    private T data;

    /**
     * 成功响应
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return 成功响应返回信息
     */
    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = 0;
        r.message = "success";
        r.data = data;
        return r;
    }

    /**
     * 失败响应
     *
     * @param code    业务错误码
     * @param message 提示信息
     * @param <T>     数据类型
     * @return 失败响应返回信息
     */
    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }
}
