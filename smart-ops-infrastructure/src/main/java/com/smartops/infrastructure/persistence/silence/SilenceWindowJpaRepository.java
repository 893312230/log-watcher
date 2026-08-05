package com.smartops.infrastructure.persistence.silence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 告警静默窗口 Spring Data JPA 仓库。
 *
 * @author smartops
 * @since 1.0.0
 */
public interface SilenceWindowJpaRepository extends JpaRepository<SilenceWindowEntity, Long> {
}
