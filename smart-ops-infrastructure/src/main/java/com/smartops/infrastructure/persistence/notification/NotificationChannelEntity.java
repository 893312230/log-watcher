package com.smartops.infrastructure.persistence.notification;

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
 * 通知渠道 JPA 实体（表 notification_channel）。
 *
 * @author smartops
 * @since 1.0.0
 */
@Getter
@Setter
@Entity
@Table(name = "notification_channel")
public class NotificationChannelEntity {

    /** 主键（自增）。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 渠道名称。 */
    @Column(nullable = false, length = 128)
    private String name;

    /** 类型（WEBHOOK / SLACK / EMAIL / DINGTALK）。 */
    @Column(nullable = false, length = 32)
    private String type;

    /** Webhook URL 或 SMTP 地址。 */
    @Column(nullable = false, length = 1024)
    private String targetUrl;

    /** 是否启用。 */
    @Column(nullable = false)
    private boolean enabled;

    /** 创建时间。 */
    @Column(nullable = false)
    private Instant createdAt;

    /** 更新时间。 */
    @Column(nullable = false)
    private Instant updatedAt;
}
