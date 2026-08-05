package com.smartops.infrastructure.persistence.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 审计事件 Spring Data JPA 仓库。
 *
 * <p>动态条件查询走 {@link JpaSpecificationExecutor}。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public interface AuditEventJpaRepository extends JpaRepository<AuditEventEntity, Long>,
        JpaSpecificationExecutor<AuditEventEntity> {
}
