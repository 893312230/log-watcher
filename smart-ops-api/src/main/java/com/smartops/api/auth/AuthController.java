package com.smartops.api.auth;

import com.smartops.api.dto.LoginRequest;
import com.smartops.infrastructure.persistence.user.SysUserEntity;
import com.smartops.infrastructure.persistence.user.SysUserJpaRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * 认证 REST 入口（阶段十二用户体系）。
 *
 * <p>登录接口签发 JWT；该路径在 SecurityConfig 中放行（permitAll）。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final SysUserJpaRepository userRepository;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /**
     * 构造认证控制器。
     *
     * @param userRepository 用户仓库
     * @param jwtService     JWT 签发服务
     */
    public AuthController(SysUserJpaRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    /**
     * 用户名密码登录，成功返回 JWT 与用户信息。
     *
     * @param request 登录请求（username/password）
     * @return {token, username, role}
     */
    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request) {
        SysUserEntity user = userRepository.findByUsername(request.username()).orElse(null);
        if (user == null || !encoder.matches(request.password(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        return Map.of(
                "token", jwtService.issue(user.getUsername(), user.getRole()),
                "username", user.getUsername(),
                "role", user.getRole());
    }

    /**
     * 刷新令牌：携带未过期 JWT 换取新令牌（滑动续期，避免 24h 硬性踢出）。
     *
     * <p>该路径在 SecurityConfig 中放行，安全性由「必须持有有效 JWT」保证。</p>
     *
     * @param authorization Authorization 头（Bearer token）
     * @return {token, username, role}
     */
    @PostMapping("/refresh")
    public Map<String, Object> refresh(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7) : null;
        var claims = jwtService.validate(token)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "令牌无效或已过期"));
        String username = claims.getSubject();
        String role = claims.get("role", String.class);
        return Map.of(
                "token", jwtService.issue(username, role),
                "username", username,
                "role", role);
    }
}
