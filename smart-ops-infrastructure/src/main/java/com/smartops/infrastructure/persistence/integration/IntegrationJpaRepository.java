package com.smartops.infrastructure.persistence.integration;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 第三方集成配置 Spring Data JPA 仓库。
 *
 * @author smartops
 * @since 1.0.0
 */
public interface IntegrationJpaRepository extends JpaRepository<IntegrationEntity, Long> {
}
