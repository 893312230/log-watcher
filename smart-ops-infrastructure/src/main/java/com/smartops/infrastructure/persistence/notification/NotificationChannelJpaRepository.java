package com.smartops.infrastructure.persistence.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 通知渠道 Spring Data JPA 仓库。
 *
 * @author smartops
 * @since 1.0.0
 */
public interface NotificationChannelJpaRepository extends JpaRepository<NotificationChannelEntity, Long> {

    /**
     * 查询全部启用渠道。
     *
     * @return 启用渠道列表
     */
    List<NotificationChannelEntity> findByEnabledTrue();
}
