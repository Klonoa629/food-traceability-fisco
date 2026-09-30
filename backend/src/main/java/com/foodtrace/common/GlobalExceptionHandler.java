package com.foodtrace.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理：统一把异常转换为 Result 响应
 *
 * @author Microft0629
 * @since 2026-09-10
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常
     *
     * @param e 业务异常
     * @return 带业务错误码的失败响应
     */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验失败
     *
     * @param e 校验异常
     * @return 错误码与校验信息
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().isEmpty()
                ? "参数错误" : e.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return Result.fail(400, msg);
    }

    /**
     * 请求体不可读（JSON 格式或编码错误）
     *
     * @param e 请求体解析异常
     * @return 错误码与通用提示
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleUnreadable(HttpMessageNotReadableException e) {
        return Result.fail(400, "请求体格式错误或编码非 UTF-8");
    }

    /**
     * 链路异常（链读写失败或签名服务不可达），透出具体原因
     *
     * @param e 链路相关异常
     * @return 502 与原因文本
     */
    @ExceptionHandler({IllegalStateException.class, RestClientException.class})
    public Result<Void> handleChain(Exception e) {
        return Result.fail(ErrorCode.CHAIN_REJECTED.getCode(), e.getMessage());
    }

    /**
     * 未知路径（无对应接口或静态资源）
     *
     * <p>Boot 3.2+ 对未匹配路径抛 NoResourceFoundException，
     * 需在兜底分支前单独处理，否则会被包装成 500。
     *
     * @param e 静态资源未找到异常
     * @return 404 与提示
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNotFound(NoResourceFoundException e) {
        return Result.fail(404, "接口不存在");
    }

    /**
     * 非预期异常
     *
     * @param e 未识别异常
     * @return 错误码与通用提示
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("未处理异常", e);
        return Result.fail(500, "服务内部错误");
    }
}
