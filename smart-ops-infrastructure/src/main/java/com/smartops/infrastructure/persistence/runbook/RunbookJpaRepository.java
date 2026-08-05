package com.smartops.infrastructure.persistence.runbook;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Runbook 定义 Spring Data JPA 仓库。
 *
 * @author smartops
 * @since 1.0.0
 */
public interface RunbookJpaRepository extends JpaRepository<RunbookEntity, Long> {
}
