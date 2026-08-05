package com.smartops.api.config;

import com.smartops.api.auth.JwtAuthFilter;
import com.smartops.api.auth.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置（阶段十二用户体系 + RBAC）。
 *
 * <p>认证：{@link JwtAuthFilter}（JWT 优先，静态 Token 兜底）。
 * 鉴权（RBAC）：
 * <ul>
 *   <li>公开：/api/health、/api/auth/login、actuator、swagger</li>
 *   <li>GET /api/**：ADMIN 与 VIEWER 均可</li>
 *   <li>非 GET /api/**：仅 ADMIN</li>
 * </ul></p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 静态 API Token（兼容机器调用）。
     * 仅经环境变量 SMARTOPS_API_TOKEN 配置；未配置时置空，静态 Token 认证关闭，
     * 避免源码中的默认值成为任何部署的可用凭据。
     */
    static final String API_TOKEN =
            System.getenv().getOrDefault("SMARTOPS_API_TOKEN", "");

    /**
     * 认证过滤器 Bean。
     *
     * @param jwtService JWT 校验服务
     * @return 认证过滤器
     */
    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtService jwtService) {
        return new JwtAuthFilter(jwtService, API_TOKEN);
    }

    /**
     * 安全过滤链（无状态 + RBAC）。
     *
     * @param http          HttpSecurity 构建器
     * @param jwtAuthFilter 认证过滤器
     * @return 过滤链
     * @throws Exception 配置异常
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http.csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/health", "/api/auth/login", "/api/auth/refresh",
                        "/actuator/**", "/swagger-ui/**", "/v3/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole("ADMIN", "VIEWER")
                .requestMatchers("/api/**").hasRole("ADMIN")
                .anyRequest().permitAll())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
