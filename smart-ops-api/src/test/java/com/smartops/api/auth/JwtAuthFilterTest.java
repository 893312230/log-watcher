package com.smartops.api.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link JwtAuthFilter} 单元测试。
 *
 * @author smartops
 * @since 1.0.0
 */
class JwtAuthFilterTest {

    private static final String SECRET = "filter-test-secret-0123456789abcdef";
    private static final String STATIC_TOKEN = "static-token-1";

    private final JwtService jwtService = new JwtService(SECRET);
    private final JwtAuthFilter filter = new JwtAuthFilter(jwtService, STATIC_TOKEN);

    @BeforeEach
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse doFilter(String uri, String authorization) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
        if (authorization != null) {
            req.addHeader("Authorization", authorization);
        }
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req, resp, new MockFilterChain());
        return resp;
    }

    @Test
    @DisplayName("有效 JWT → 按 claims 角色建立认证")
    void should_authenticateWithJwtRole_when_tokenValid() throws Exception {
        String token = jwtService.issue("viewer1", "VIEWER");

        MockHttpServletResponse resp = doFilter("/api/alerts", "Bearer " + token);

        assertThat(resp.getStatus()).isEqualTo(200);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getName()).isEqualTo("viewer1");
        assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_VIEWER"));
    }

    @Test
    @DisplayName("静态 Token → api-user 兼容认证（ROLE_ADMIN）")
    void should_authenticateAsApiUser_when_staticToken() throws Exception {
        MockHttpServletResponse resp = doFilter("/api/alerts", "Bearer " + STATIC_TOKEN);

        assertThat(resp.getStatus()).isEqualTo(200);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getName()).isEqualTo("api-user");
        assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @Test
    @DisplayName("无效凭据访问 /api/** → 401 JSON")
    void should_return401_when_credentialInvalid() throws Exception {
        MockHttpServletResponse resp = doFilter("/api/alerts", "Bearer bad-token");

        assertThat(resp.getStatus()).isEqualTo(401);
        assertThat(resp.getContentAsString()).contains("UNAUTHORIZED");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("缺少 Authorization 头访问 /api/** → 401")
    void should_return401_when_headerMissing() throws Exception {
        MockHttpServletResponse resp = doFilter("/api/runbooks", null);

        assertThat(resp.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("公开路径无凭据直接放行")
    void should_passThrough_when_publicPath() throws Exception {
        assertThat(doFilter("/api/health", null).getStatus()).isEqualTo(200);
        assertThat(doFilter("/api/auth/login", null).getStatus()).isEqualTo(200);
        assertThat(doFilter("/actuator/prometheus", null).getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("非 /api 路径无凭据放行（前端静态资源）")
    void should_passThrough_when_nonApiPath() throws Exception {
        assertThat(doFilter("/index.html", null).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("JWT 缺 role claim 时按 VIEWER 兜底")
    void should_defaultViewer_when_roleClaimMissing() throws Exception {
        String token = io.jsonwebtoken.Jwts.builder()
                .subject("norole")
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        MockHttpServletResponse resp = doFilter("/api/alerts", "Bearer " + token);

        assertThat(resp.getStatus()).isEqualTo(200);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_VIEWER"));
    }
}
