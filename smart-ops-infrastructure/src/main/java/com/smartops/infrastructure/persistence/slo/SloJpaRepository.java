package com.smartops.infrastructure.persistence.slo;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 服务等级目标 Spring Data JPA 仓库。
 *
 * @author smartops
 * @since 1.0.0
 */
public interface SloJpaRepository extends JpaRepository<SloEntity, Long> {
}
