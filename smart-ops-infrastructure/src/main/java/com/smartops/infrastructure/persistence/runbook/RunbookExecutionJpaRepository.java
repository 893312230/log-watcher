package com.smartops.infrastructure.persistence.runbook;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Runbook 执行记录 Spring Data JPA 仓库。
 *
 * @author smartops
 * @since 1.0.0
 */
public interface RunbookExecutionJpaRepository extends JpaRepository<RunbookExecutionEntity, Long> {

    /**
     * 查询某 Runbook 的执行历史（开始时间倒序，最多 100 条）。
     *
     * @param runbookId Runbook id
     * @return 执行记录列表
     */
    List<RunbookExecutionEntity> findTop100ByRunbookIdOrderByStartedAtDesc(Long runbookId);
}
