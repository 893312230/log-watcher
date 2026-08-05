package com.smartops.api.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CorsConfig} 单元测试。
 *
 * @author smartops
 * @since 1.0.0
 */
class CorsConfigTest {

    @Test
    @DisplayName("corsConfigurer 注册 /api/** 跨域映射")
    void should_registerApiCorsMapping() {
        CorsRegistry registry = mock(CorsRegistry.class);
        CorsRegistration registration = mock(CorsRegistration.class);
        when(registry.addMapping(anyString())).thenReturn(registration);
        when(registration.allowedOriginPatterns(org.mockito.ArgumentMatchers.any(String[].class))).thenReturn(registration);
        when(registration.allowedMethods(org.mockito.ArgumentMatchers.any(String[].class))).thenReturn(registration);
        when(registration.allowedHeaders(org.mockito.ArgumentMatchers.any(String[].class))).thenReturn(registration);
        when(registration.allowCredentials(false)).thenReturn(registration);

        WebMvcConfigurer configurer = new CorsConfig().corsConfigurer();
        configurer.addCorsMappings(registry);

        verify(registry).addMapping("/api/**");
        verify(registration).allowCredentials(false);
    }
}
