package com.smartops.infrastructure.persistence.webhook;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Webhook 投递日志 Spring Data 仓库。
 *
 * @author smartops
 * @since 1.0.0
 */
public interface WebhookDeliveryJpaRepository extends JpaRepository<WebhookDeliveryEntity, Long> {

    /**
     * 查询指定订阅的投递日志（时间倒序，最多 100 条）。
     *
     * @param subscriptionId 订阅 id
     * @return 投递日志列表
     */
    List<WebhookDeliveryEntity> findTop100BySubscriptionIdOrderByCreatedAtDesc(long subscriptionId);

    /**
     * 查询全部订阅的最近投递日志（时间倒序，最多 100 条）。
     *
     * @return 投递日志列表
     */
    List<WebhookDeliveryEntity> findTop100ByOrderByCreatedAtDesc();
}
