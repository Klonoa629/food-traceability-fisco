package com.foodtrace.common;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全局异常处理测试：链路异常透出与兜底
 *
 * @author Microft0629
 * @since 2026-09-30
 */
class GlobalExceptionHandlerTest {
    /** 被测处理器 */
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void chainStateExceptionShouldSurfaceMessage() {
        Result<Void> result = handler.handleChain(
                new IllegalStateException("链上查询 getProduct 失败：timeout"));
        assertThat(result.getCode()).isEqualTo(502);
        assertThat(result.getMessage()).contains("getProduct");
    }

    @Test
    void signClientExceptionShouldSurfaceMessage() {
        Result<Void> result = handler.handleChain(
                new ResourceAccessException("Connection refused"));
        assertThat(result.getCode()).isEqualTo(502);
        assertThat(result.getMessage()).contains("Connection refused");
    }

    @Test
    void unexpectedExceptionShouldHideDetails() {
        Result<Void> result = handler.handleOther(new RuntimeException("secret detail"));
        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMessage()).doesNotContain("secret");
    }
}
