package com.smartops.api.dto;

import com.smartops.infrastructure.persistence.webhook.WebhookSubscriptionEntity;

import java.time.Instant;

/**
 * Webhook 订阅视图 DTO。
 *
 * <p>对外输出时刻意剔除 {@code secret}（HMAC 签名密钥）——
 * 订阅列表 GET 对 VIEWER 角色开放，密钥明文外泄会导致签名伪造。
 * 同时以 {@code hasSecret} 告知前端该订阅是否已配置密钥，
 * 便于表单回显「已设置，留空保持不变」。</p>
 *
 * <p>线程安全：record 不可变。</p>
 *
 * @author smartops
 * @since 1.0.0
 *
 * @param id          订阅 id
 * @param name        订阅名称
 * @param url         投递地址
 * @param eventTypes  订阅事件类型（逗号分隔）
 * @param hasSecret   是否已配置签名密钥
 * @param enabled     是否启用
 * @param retryCount  失败重试次数
 * @param createdAt   创建时间
 */
public record WebhookSubscriptionView(
        Long id,
        String name,
        String url,
        String eventTypes,
        boolean hasSecret,
        boolean enabled,
        int retryCount,
        Instant createdAt
) {

    /**
     * 从实体构建视图（secret 转 hasSecret 布尔标志）。
     *
     * @param e 订阅实体
     * @return 视图
     */
    public static WebhookSubscriptionView from(WebhookSubscriptionEntity e) {
        return new WebhookSubscriptionView(
                e.getId(), e.getName(), e.getUrl(), e.getEventTypes(),
                e.getSecret() != null && !e.getSecret().isBlank(),
                e.isEnabled(), e.getRetryCount(), e.getCreatedAt());
    }
}
