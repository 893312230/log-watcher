package com.smartops.domain.notification;

import java.time.Instant;
import java.util.Objects;

/**
 * 通知渠道（阶段七 ChatOps）。
 *
 * @param id         持久化 id
 * @param name       渠道名称
 * @param type       类型（WEBHOOK / SLACK / EMAIL / DINGTALK）
 * @param url        Webhook URL 或 SMTP 地址
 * @param enabled    是否启用
 * @param createdAt  创建时间
 * @param updatedAt  更新时间
 */
public record NotificationChannel(
        Long id, String name, String type, String url,
        boolean enabled, Instant createdAt, Instant updatedAt
) {
    public NotificationChannel {
        Objects.requireNonNull(name, "名称不能为 null");
        if (type == null || type.isBlank()) type = "WEBHOOK";
    }
}
