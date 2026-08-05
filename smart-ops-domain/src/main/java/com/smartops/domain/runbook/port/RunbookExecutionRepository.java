package com.smartops.domain.runbook.port;

import com.smartops.domain.runbook.RunbookExecution;

import java.util.List;
import java.util.Optional;

/**
 * Runbook 执行历史持久化端口。
 */
public interface RunbookExecutionRepository {

    /**
     * 保存执行记录（含步骤结果，整体替换）。
     *
     * @param execution 执行记录（id 为 null 表示新建）
     * @return 含分配 id 的执行记录
     */
    RunbookExecution save(RunbookExecution execution);

    /**
     * 按 id 查询（含步骤结果）。
     *
     * @param id 主键
     * @return 执行记录或空
     */
    Optional<RunbookExecution> findById(long id);

    /**
     * 查询某 Runbook 的执行历史（按开始时间倒序）。
     *
     * @param runbookId Runbook id
     * @return 执行记录列表
     */
    List<RunbookExecution> findByRunbookId(long runbookId);
}
