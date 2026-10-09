package com.foodtrace.security;

import com.foodtrace.common.BizException;
import com.foodtrace.common.ErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 限流服务测试：登录锁定与 IP 限速的窗口语义
 *
 * @author Microft0629
 * @since 2026-10-05
 */
class RateLimitServiceTest {
    /** 可推进的时钟 */
    private final AtomicLong now = new AtomicLong(1_700_000_000_000L);
    private RateLimitService service;

    /**
     * 以可控时钟构造被测服务
     */
    @BeforeEach
    void setUp() {
        service = new RateLimitService(now::get, new SimpleMeterRegistry());
    }

    /**
     * 构造指定 IP 的请求
     */
    private MockHttpServletRequest request(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        return request;
    }

    @Test
    void loginShouldBlockAfterFiveFailures() {
        for (int i = 0; i < 5; i++) {
            service.recordLoginFailure("farm_a");
        }
        assertThatThrownBy(() -> service.checkLoginBlocked("farm_a"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.TOO_MANY_REQUESTS.getCode());
    }

    @Test
    void loginBlockShouldResetOnSuccess() {
        for (int i = 0; i < 4; i++) {
            service.recordLoginFailure("farm_a");
        }
        service.recordLoginSuccess("farm_a");
        assertThatCode(() -> service.checkLoginBlocked("farm_a")).doesNotThrowAnyException();
    }

    @Test
    void loginBlockShouldExpireAfterWindow() {
        for (int i = 0; i < 5; i++) {
            service.recordLoginFailure("farm_a");
        }
        now.addAndGet(10 * 60 * 1000L + 1);
        assertThatCode(() -> service.checkLoginBlocked("farm_a")).doesNotThrowAnyException();
    }

    @Test
    void publicIpLimitShouldRejectAtThirtyFirstRequest() {
        for (int i = 0; i < 30; i++) {
            assertThatCode(() -> service.checkIpLimit(request("1.2.3.4"), "public"))
                    .doesNotThrowAnyException();
        }
        assertThatThrownBy(() -> service.checkIpLimit(request("1.2.3.4"), "public"))
                .isInstanceOf(BizException.class);
        // 不同 IP 不受影响
        assertThatCode(() -> service.checkIpLimit(request("5.6.7.8"), "public"))
                .doesNotThrowAnyException();
    }

    @Test
    void ipLimitWindowShouldResetAfterOneMinute() {
        for (int i = 0; i < 30; i++) {
            service.checkIpLimit(request("1.2.3.4"), "public");
        }
        now.addAndGet(60 * 1000L + 1);
        assertThatCode(() -> service.checkIpLimit(request("1.2.3.4"), "public"))
                .doesNotThrowAnyException();
    }

    @Test
    void forwardedForHeaderShouldTakePrecedence() {
        MockHttpServletRequest forwarded = new MockHttpServletRequest();
        forwarded.setRemoteAddr("9.9.9.9");
        forwarded.addHeader("X-Forwarded-For", "1.1.1.1, 2.2.2.2");
        // 1.1.1.1 与 9.9.9.9 计数相互独立：对代理 IP 打满不影响真实客户端
        for (int i = 0; i < 20; i++) {
            service.checkIpLimit(request("9.9.9.9"), "auth");
        }
        assertThatCode(() -> service.checkIpLimit(forwarded, "auth")).doesNotThrowAnyException();
    }
}
