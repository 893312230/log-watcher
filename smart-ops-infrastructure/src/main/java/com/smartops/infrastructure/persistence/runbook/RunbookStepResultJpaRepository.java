package com.smartops.infrastructure.persistence.runbook;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * Runbook 步骤执行结果 Spring Data JPA 仓库。
 *
 * @author smartops
 * @since 1.0.0
 */
public interface RunbookStepResultJpaRepository extends JpaRepository<RunbookStepResultEntity, Long> {

    /**
     * 查询某次执行的全部步骤结果（序号升序）。
     *
     * @param executionId 执行记录 id
     * @return 步骤结果列表
     */
    List<RunbookStepResultEntity> findByExecutionIdOrderBySeqAsc(Long executionId);

    /**
     * 删除某次执行的全部步骤结果。
     *
     * @param executionId 执行记录 id
     */
    void deleteByExecutionId(Long executionId);

    /**
     * 批量查询多次执行的步骤结果（内存分组排序，避免 N+1）。
     *
     * @param executionIds 执行记录 id 集合
     * @return 步骤结果列表（无序，调用方按 executionId 分组后按 seq 排序）
     */
    List<RunbookStepResultEntity> findByExecutionIdIn(Collection<Long> executionIds);
}
