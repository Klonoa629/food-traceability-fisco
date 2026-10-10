package com.foodtrace.security;

import com.foodtrace.entity.SysUser;
import com.foodtrace.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

/**
 * JWT 认证过滤器
 *
 * <p>解析 Authorization: Bearer 头并查库核实账户状态
 *
 * @author Microft0629
 * @since 2026-09-16
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    /** JWT 工具 */
    private final JwtUtil jwtUtil;

    /** 账户查询服务 */
    private final UserService userService;

    /**
     * 请求认证
     *
     * @param request     请求
     * @param response    响应
     * @param filterChain 过滤链
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String header = request.getHeader("Authorization");
            // 仅处理 Bearer 令牌
            if (header != null && header.startsWith("Bearer ")) {
                String username = jwtUtil.verify(header.substring(7));
                SysUser user = userService.findByUsername(username);
                // 仅生效账户可认证成功，吊销或待审批账户一律视为未登录
                if (user != null && Integer.valueOf(1).equals(user.getStatus())) {
                    // 比对密码版本：改密后旧令牌立即失效
                    int tokenVer = jwtUtil.passwordVersion(header.substring(7));
                    int dbVer = user.getPwdVersion() == null ? 0 : user.getPwdVersion();
                    if (tokenVer != dbVer) {
                        throw new IllegalStateException("密码已修改");
                    }
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>(
                            List.of(new SimpleGrantedAuthority("ROLE_USER")));
                    if (Boolean.TRUE.equals(user.getIsRegulator())) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_REGULATOR"));
                    }
                    LoginUser principal = new LoginUser(user.getId(), user.getUsername(),
                            Boolean.TRUE.equals(user.getIsRegulator()), user.getRole() == null ? 0 : user.getRole(),
                            user.getSignUserId(), user.getChainAddress());
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(principal, null, authorities));
                }
            }
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            // 无效令牌按未认证放行，由安全链返回 401
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
        }
    }
}
