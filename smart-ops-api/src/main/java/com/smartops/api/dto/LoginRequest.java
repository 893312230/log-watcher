package com.smartops.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求 DTO。
 *
 * @author smartops
 * @since 1.0.0
 *
 * @param username 用户名（必填）
 * @param password 密码（必填）
 */
public record LoginRequest(
        @NotBlank(message = "用户名不能为空") String username,
        @NotBlank(message = "密码不能为空") String password
) {
}
