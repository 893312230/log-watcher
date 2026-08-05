package com.smartops.api.auth;

import com.smartops.infrastructure.persistence.user.SysUserEntity;
import com.smartops.infrastructure.persistence.user.SysUserJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link AuthController} Web 层测试。
 *
 * @author smartops
 * @since 1.0.0
 */
@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SysUserJpaRepository userRepository;

    @MockitoBean
    private JwtService jwtService;

    private SysUserEntity admin() {
        SysUserEntity u = new SysUserEntity();
        u.setId(1L);
        u.setUsername("admin");
        u.setPassword(new BCryptPasswordEncoder().encode("admin123"));
        u.setRole("ADMIN");
        u.setCreatedAt(Instant.now());
        return u;
    }

    @Test
    @DisplayName("登录成功返回 JWT 与用户信息")
    void should_returnToken_when_credentialsValid() throws Exception {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin()));
        when(jwtService.issue("admin", "ADMIN")).thenReturn("jwt-token-1");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token-1"))
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @DisplayName("密码错误返回 401")
    void should_return401_when_passwordWrong() throws Exception {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin()));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("用户不存在返回 401")
    void should_return401_when_userMissing() throws Exception {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ghost\",\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("缺少用户名字段返回 400（@Valid）")
    void should_return400_when_usernameAbsent() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("refresh 携带有效令牌换发新令牌")
    void should_issueNewToken_when_refreshTokenValid() throws Exception {
        io.jsonwebtoken.Claims claims = org.mockito.Mockito.mock(io.jsonwebtoken.Claims.class);
        when(claims.getSubject()).thenReturn("admin");
        when(claims.get("role", String.class)).thenReturn("ADMIN");
        when(jwtService.validate("old-token")).thenReturn(Optional.of(claims));
        when(jwtService.issue("admin", "ADMIN")).thenReturn("jwt-token-2");

        mockMvc.perform(post("/api/auth/refresh")
                        .header("Authorization", "Bearer old-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token-2"))
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @DisplayName("refresh 令牌无效或缺失返回 401")
    void should_return401_when_refreshTokenInvalid() throws Exception {
        when(jwtService.validate("bad-token")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/refresh")
                        .header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }
}
