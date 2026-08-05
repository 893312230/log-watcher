package com.smartops.infrastructure.persistence.runbook;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * Runbook 步骤 Spring Data JPA 仓库。
 *
 * @author smartops
 * @since 1.0.0
 */
public interface RunbookStepJpaRepository extends JpaRepository<RunbookStepEntity, Long> {

    /**
     * 按 Runbook id 查询步骤（序号升序）。
     *
     * @param runbookId Runbook id
     * @return 步骤列表
     */
    List<RunbookStepEntity> findByRunbookIdOrderBySeqAsc(Long runbookId);

    /**
     * 删除某 Runbook 的全部步骤。
     *
     * @param runbookId Runbook id
     */
    void deleteByRunbookId(Long runbookId);

    /**
     * 批量查询多个 Runbook 的步骤（内存分组排序，避免 N+1）。
     *
     * @param runbookIds Runbook id 集合
     * @return 步骤列表（无序，调用方按 runbookId 分组后按 seq 排序）
     */
    List<RunbookStepEntity> findByRunbookIdIn(Collection<Long> runbookIds);
}
