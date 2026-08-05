package com.smartops.infrastructure.persistence.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Webhook 订阅 JPA 实体（表 webhook_subscription）。
 *
 * @author smartops
 * @since 1.0.0
 */
@Getter
@Setter
@Entity
@Table(name = "webhook_subscription")
public class WebhookSubscriptionEntity {

    /** 主键（自增）。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 订阅名称。 */
    @Column(nullable = false, length = 128)
    private String name;

    /** 投递目标 URL。 */
    @Column(nullable = false, length = 1024)
    private String url;

    /** 订阅的事件类型（逗号分隔，如 ALERT_CREATED,RUNBOOK_FAILED）。 */
    @Column(nullable = false, length = 512)
    private String eventTypes;

    /** HMAC-SHA256 签名密钥（为空则不签名）。 */
    @Column(length = 256)
    private String secret;

    /** 是否启用。 */
    @Column(nullable = false)
    private boolean enabled;

    /** 失败重试次数。 */
    @Column(nullable = false)
    private int retryCount;

    /** 创建时间。 */
    @Column(nullable = false)
    private Instant createdAt;
}
