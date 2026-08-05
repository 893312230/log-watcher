package com.smartops.domain.runbook;

import java.time.Instant;
import java.util.List;

/**
 * Runbook 一次执行的完整记录。
 *
 * <p>状态机：RUNNING → SUCCESS / FAILED；执行中的记录 finishedAt 为 null。</p>
 *
 * @param id          主键（新建为 null）
 * @param runbookId   所属 Runbook id
 * @param startedAt   开始时间
 * @param finishedAt  结束时间（运行中为 null）
 * @param status      执行状态（RUNNING / SUCCESS / FAILED）
 * @param stepResults 各步骤结果（按 seq 升序）
 */
public record RunbookExecution(
        Long id, Long runbookId, Instant startedAt, Instant finishedAt,
        String status, List<StepResult> stepResults
) {
    public RunbookExecution {
        stepResults = stepResults == null ? List.of() : List.copyOf(stepResults);
    }

    /**
     * 创建一条 RUNNING 状态的执行记录。
     *
     * @param runbookId 所属 Runbook id
     * @param now       当前时间
     * @return 新执行记录（id 未分配）
     */
    public static RunbookExecution start(long runbookId, Instant now) {
        return new RunbookExecution(null, runbookId, now, null, "RUNNING", List.of());
    }

    /**
     * 生成终态执行记录（保留 id 与 startedAt）。
     *
     * @param status  最终状态（SUCCESS / FAILED）
     * @param results 步骤结果列表
     * @param now     结束时间
     * @return 终态执行记录
     */
    public RunbookExecution finish(String status, List<StepResult> results, Instant now) {
        return new RunbookExecution(id, runbookId, startedAt, now, status, results);
    }
}
