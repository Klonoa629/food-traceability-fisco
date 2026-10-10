package com.foodtrace.controller;

import com.foodtrace.common.Result;
import com.foodtrace.dto.ChangePasswordRequest;
import com.foodtrace.dto.LoginRequest;
import com.foodtrace.dto.LoginResponse;
import com.foodtrace.dto.RegisterRequest;
import com.foodtrace.security.LoginUser;
import com.foodtrace.security.RateLimitService;
import com.foodtrace.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口：注册与登录（匿名可访问）
 *
 * @author Microft0629
 * @since 2026-09-17
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    /** 账户服务 */
    private final UserService userService;
    /** 限流服务 */
    private final RateLimitService rateLimitService;

    /**
     * 机构注册，注册后待监管审批
     *
     * @param request     注册请求
     * @param httpRequest 当前请求（用于 IP 限流）
     * @return 新账户 id
     */
    @PostMapping("/register")
    public Result<Long> register(@Valid @RequestBody RegisterRequest request,
                                 HttpServletRequest httpRequest) {
        rateLimitService.checkIpLimit(httpRequest, "auth");
        return Result.ok(userService.register(request));
    }

    /**
     * 账户登录，签发 JWT
     *
     * @param request 登录请求
     * @return 令牌与账户信息
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(userService.login(request));
    }

    /**
     * 修改密码，改密后当前令牌立即失效（需重新登录）
     *
     * @param request 改密请求
     * @param user    当前登录账户
     * @return 成功提示
     */
    @PostMapping("/change-password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                       @AuthenticationPrincipal LoginUser user) {
        userService.changePassword(request, user);
        return Result.ok(null);
    }
}
