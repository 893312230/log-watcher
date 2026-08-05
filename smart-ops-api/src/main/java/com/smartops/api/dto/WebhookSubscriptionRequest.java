package com.smartops.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Webhook 订阅创建/更新请求 DTO。
 *
 * <p>{@code secret} 语义：创建时可空（不签名）；更新时 {@code null}
 * 表示保持原值不变，空串表示清除密钥。</p>
 *
 * @author smartops
 * @since 1.0.0
 *
 * @param name       订阅名称（必填）
 * @param url        投递地址（必填，经 SSRF 校验）
 * @param eventTypes 订阅事件类型列表
 * @param secret     HMAC 签名密钥（可选）
 * @param enabled    是否启用（默认 true）
 * @param retryCount 失败重试次数（默认 3）
 */
public record WebhookSubscriptionRequest(
        @NotBlank(message = "订阅名称不能为空") String name,
        @NotBlank(message = "投递地址不能为空") String url,
        List<String> eventTypes,
        String secret,
        Boolean enabled,
        Integer retryCount
) {
}
