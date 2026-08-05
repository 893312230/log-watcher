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
 * Webhook 投递日志 JPA 实体（表 webhook_delivery）。
 *
 * @author smartops
 * @since 1.0.0
 */
@Getter
@Setter
@Entity
@Table(name = "webhook_delivery")
public class WebhookDeliveryEntity {

    /** 主键（自增）。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属订阅 id。 */
    @Column(nullable = false)
    private Long subscriptionId;

    /** 事件类型。 */
    @Column(nullable = false, length = 64)
    private String eventType;

    /** 投递报文（JSON）。 */
    @Column(columnDefinition = "TEXT")
    private String payload;

    /** 目标返回的 HTTP 状态码（网络异常时为空）。 */
    private Integer statusCode;

    /** 本次投递是否成功。 */
    @Column(nullable = false)
    private boolean success;

    /** 第几次尝试（从 1 开始）。 */
    @Column(nullable = false)
    private int attempt;

    /** 投递时间。 */
    @Column(nullable = false)
    private Instant createdAt;
}
