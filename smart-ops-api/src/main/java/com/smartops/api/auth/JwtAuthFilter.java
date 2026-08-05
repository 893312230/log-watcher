package com.smartops.api.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * API 认证过滤器（阶段十二用户体系）。
 *
 * <p>优先级：JWT 优先、静态 Token 兜底（机器调用兼容）。
 * 公开路径（健康检查/登录/actuator/swagger）直接放行；
 * 其余 /api/** 无有效凭据时返回 401 JSON。鉴权（角色）由
 * SecurityConfig 的 authorizeHttpRequests 规则执行。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final String staticToken;

    /**
     * 构造认证过滤器。
     *
     * @param jwtService  JWT 校验服务
     * @param staticToken 静态 API Token（兼容机器调用）
     */
    public JwtAuthFilter(JwtService jwtService, String staticToken) {
        this.jwtService = jwtService;
        this.staticToken = staticToken;
    }

    /**
     * 执行认证逻辑。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp,
                                    FilterChain chain) throws ServletException, IOException {
        String uri = req.getRequestURI();
        if (isPublic(uri)) {
            chain.doFilter(req, resp);
            return;
        }
        String token = bearerToken(req);
        if (token != null) {
            Optional<Claims> claims = jwtService.validate(token);
            if (claims.isPresent()) {
                String role = claims.get().get("role", String.class);
                authenticate(claims.get().getSubject(), token,
                        "ROLE_" + (role == null ? "VIEWER" : role));
                chain.doFilter(req, resp);
                return;
            }
            if (!staticToken.isBlank() && staticToken.equals(token)) {
                authenticate("api-user", token, "ROLE_ADMIN");
                chain.doFilter(req, resp);
                return;
            }
        }
        if (uri.startsWith("/api/")) {
            resp.setStatus(HttpStatus.UNAUTHORIZED.value());
            resp.setContentType("application/json");
            resp.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"缺少有效的认证凭据\"}");
            return;
        }
        chain.doFilter(req, resp);
    }

    private void authenticate(String username, String credentials, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, credentials,
                        List.of(new SimpleGrantedAuthority(role))));
    }

    private static String bearerToken(HttpServletRequest req) {
        String header = req.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
    }

    private static boolean isPublic(String uri) {
        return uri.equals("/api/health") || uri.equals("/api/auth/login")
                || uri.startsWith("/actuator") || uri.startsWith("/swagger-ui")
                || uri.startsWith("/v3");
    }
}
