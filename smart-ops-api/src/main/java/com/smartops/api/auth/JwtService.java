package com.smartops.api.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * JWT 签发与校验服务（阶段十二用户体系）。
 *
 * <p>HS256 对称签名，密钥来自 {@code smartops.auth.jwt-secret}
 * （生产经环境变量注入，至少 32 字节）；令牌有效期 24 小时，
 * claims 含 sub（用户名）与 role。</p>
 *
 * <p>线程安全：jjwt 的 parser/builder 均线程安全，本类无内部状态。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Component
public class JwtService {

    /** 令牌有效期：24 小时。 */
    static final Duration TOKEN_TTL = Duration.ofHours(24);

    private final SecretKey key;

    /**
     * 构造 JWT 服务。
     *
     * @param secret 签名密钥（至少 32 字节，不足时抛异常）
     */
    public JwtService(@Value("${smartops.auth.jwt-secret:smartops-dev-jwt-secret-0123456789abcdef}") String secret) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT 密钥长度不足 32 字节");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发令牌。
     *
     * @param username 用户名
     * @param role     角色（ADMIN / VIEWER）
     * @return JWT 字符串
     */
    public String issue(String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(TOKEN_TTL)))
                .signWith(key)
                .compact();
    }

    /**
     * 校验令牌并解析 claims。
     *
     * @param token JWT 字符串
     * @return 有效时返回 claims，无效或过期返回空
     */
    public Optional<Claims> validate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
