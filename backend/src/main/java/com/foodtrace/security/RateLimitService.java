package com.foodtrace.security;

import com.foodtrace.common.BizException;
import com.foodtrace.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * 轻量限流服务
 *
 * <p>两类策略：登录名维度防暴力破解（窗口内失败次数达到阈值即锁定，
 * 成功登录清零）；匿名接口按 IP 固定窗口限速。单实例内存实现，超限抛 429。
 *
 * @author Microft0629
 * @since 2026-10-05
 */
@Component
public class RateLimitService {
    /** 同一登录名窗口期内允许的最大失败次数 */
    private static final int MAX_LOGIN_FAILURES = 5;
    /** 登录失败统计窗口 */
    private static final long LOGIN_WINDOW_MS = 10 * 60 * 1000L;
    /** 匿名接口每 IP 每分钟允许的请求数 */
    private static final Map<String, Integer> IP_LIMIT_PER_MINUTE = Map.of(
            "auth", 20, "public", 30);
    /** IP 限速窗口 */
    private static final long IP_WINDOW_MS = 60 * 1000L;
    /** 计数表超过该规模时触发过期清理，防止键无限增长 */
    private static final int CLEANUP_THRESHOLD = 10_000;

    /** 当前时间毫秒源（可注入便于测试） */
    private final LongSupplier clock;
    /** 登录名 -> 失败统计 */
    private final Map<String, FailureWindow> loginFailures = new ConcurrentHashMap<>();
    /** 范围:IP -> 请求计数 */
    private final Map<String, CountWindow> ipWindows = new ConcurrentHashMap<>();

    /**
     * 默认以系统时钟构造
     */
    public RateLimitService() {
        this(System::currentTimeMillis);
    }

    /**
     * 以指定时钟构造（测试用）
     *
     * @param clock 时间毫秒源
     */
    RateLimitService(LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * 登录前检查：失败次数超阈且仍在窗口内则拒绝
     *
     * @param username 登录名
     * @throws BizException 已被锁定时抛出 429
     */
    public void checkLoginBlocked(String username) {
        FailureWindow window = loginFailures.get(username);
        if (window != null && window.failures.get() >= MAX_LOGIN_FAILURES
                && clock.getAsLong() - window.sinceMs < LOGIN_WINDOW_MS) {
            throw new BizException(ErrorCode.TOO_MANY_REQUESTS,
                    "登录失败次数过多，请 10 分钟后再试");
        }
    }

    /**
     * 记录一次登录失败，窗口过期则重新计数
     *
     * @param username 登录名
     */
    public void recordLoginFailure(String username) {
        cleanupIfCrowded();
        long now = clock.getAsLong();
        loginFailures.compute(username, (name, window) -> {
            if (window == null || now - window.sinceMs >= LOGIN_WINDOW_MS) {
                return new FailureWindow(now);
            }
            window.failures.incrementAndGet();
            return window;
        });
    }

    /**
     * 登录成功，清除该登录名的失败计数
     *
     * @param username 登录名
     */
    public void recordLoginSuccess(String username) {
        loginFailures.remove(username);
    }

    /**
     * 匿名接口按 IP 限速检查，超限抛 429
     *
     * @param request 当前请求（取客户端 IP）
     * @param scope   限速范围（auth / public）
     * @throws BizException 超出每分钟配额时抛出 429
     */
    public void checkIpLimit(HttpServletRequest request, String scope) {
        int limit = IP_LIMIT_PER_MINUTE.getOrDefault(scope, 20);
        String key = scope + ":" + clientIp(request);
        cleanupIfCrowded();
        long now = clock.getAsLong();
        ipWindows.compute(key, (k, window) -> {
            if (window == null || now - window.sinceMs >= IP_WINDOW_MS) {
                return new CountWindow(now);
            }
            window.count.incrementAndGet();
            return window;
        });
        if (ipWindows.get(key).count.get() > limit) {
            throw new BizException(ErrorCode.TOO_MANY_REQUESTS,
                    "请求过于频繁，请稍后再试");
        }
    }

    /**
     * 取客户端 IP：优先代理透传的 X-Forwarded-For 首段
     *
     * @param request 当前请求
     * @return 客户端 IP
     */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 计数表过大时清理过期窗口
     */
    private void cleanupIfCrowded() {
        if (loginFailures.size() + ipWindows.size() < CLEANUP_THRESHOLD) {
            return;
        }
        long now = clock.getAsLong();
        loginFailures.values().removeIf(w -> now - w.sinceMs >= LOGIN_WINDOW_MS);
        ipWindows.values().removeIf(w -> now - w.sinceMs >= IP_WINDOW_MS);
    }

    /**
     * 登录失败计数窗口
     */
    private static final class FailureWindow {
        /** 窗口起点毫秒 */
        final long sinceMs;
        /** 窗口内失败次数 */
        final AtomicInteger failures = new AtomicInteger(1);

        FailureWindow(long sinceMs) {
            this.sinceMs = sinceMs;
        }
    }

    /**
     * IP 请求计数窗口
     */
    private static final class CountWindow {
        /** 窗口起点毫秒 */
        final long sinceMs;
        /** 窗口内请求次数 */
        final AtomicInteger count = new AtomicInteger(1);

        CountWindow(long sinceMs) {
            this.sinceMs = sinceMs;
        }
    }
}
