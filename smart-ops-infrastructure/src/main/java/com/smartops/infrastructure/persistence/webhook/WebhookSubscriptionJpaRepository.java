package com.smartops.infrastructure.persistence.webhook;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Webhook 订阅 Spring Data 仓库。
 *
 * @author smartops
 * @since 1.0.0
 */
public interface WebhookSubscriptionJpaRepository extends JpaRepository<WebhookSubscriptionEntity, Long> {

    /**
     * 查询全部启用订阅。
     *
     * @return 启用订阅列表
     */
    List<WebhookSubscriptionEntity> findByEnabledTrue();
}
