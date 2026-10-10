package com.foodtrace.security;

import com.foodtrace.config.ContractProperties;
import com.foodtrace.entity.SysUser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * JWT 签发与校验工具
 *
 * @author Microft0629
 * @since 2026-09-14
 */
@Component
public class JwtUtil {
    /** 签名密钥 */
    private final SecretKey key;

    /** 有效期 */
    private final Duration ttl;

    /**
     * 初始化签名密钥和有效期
     *
     * @param properties 合约与 JWT 配置
     */
    public JwtUtil(ContractProperties properties) {
        this.key = Keys.hmacShaKeyFor(
                properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
        this.ttl = Duration.ofMinutes(properties.getJwt().getTtlMinutes());
    }

    /**
     * 签发令牌
     *
     * @param user 已通过认证的账户
     * @return JWT 字符串
     */
    public String issue(SysUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("pwdVer", user.getPwdVersion() == null ? 0 : user.getPwdVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /**
     * 校验令牌并取回用户名
     *
     * @param token JWT字符串
     * @return 用户名
     * @throws io.jsonwebtoken.JwtException 签名不符或已过期时抛出
     */
    public String verify(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload().getSubject();
    }

    /**
     * 从令牌取回密码版本（供过滤器比对）
     *
     * @param token JWT字符串
     * @return 密码版本
     * @throws io.jsonwebtoken.JwtException 令牌无效时抛出
     */
    public int passwordVersion(String token) {
        Integer ver = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload().get("pwdVer", Integer.class);
        return ver == null ? 0 : ver;
    }
}
