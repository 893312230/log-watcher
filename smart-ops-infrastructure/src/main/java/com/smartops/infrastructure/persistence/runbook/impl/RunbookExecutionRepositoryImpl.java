package com.smartops.infrastructure.persistence.runbook.impl;

import com.smartops.domain.runbook.RunbookExecution;
import com.smartops.domain.runbook.StepResult;
import com.smartops.domain.runbook.port.RunbookExecutionRepository;
import com.smartops.infrastructure.persistence.runbook.RunbookExecutionEntity;
import com.smartops.infrastructure.persistence.runbook.RunbookExecutionJpaRepository;
import com.smartops.infrastructure.persistence.runbook.RunbookStepResultEntity;
import com.smartops.infrastructure.persistence.runbook.RunbookStepResultJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runbook 执行历史持久化端口的 JPA 实现。
 *
 * @author smartops
 * @since 1.0.0
 */
@Repository
public class RunbookExecutionRepositoryImpl implements RunbookExecutionRepository {

    private final RunbookExecutionJpaRepository executionJpa;
    private final RunbookStepResultJpaRepository resultJpa;

    /**
     * 构造执行历史持久化实现。
     *
     * @param executionJpa 执行记录仓库
     * @param resultJpa    步骤结果仓库
     */
    public RunbookExecutionRepositoryImpl(RunbookExecutionJpaRepository executionJpa,
                                          RunbookStepResultJpaRepository resultJpa) {
        this.executionJpa = executionJpa;
        this.resultJpa = resultJpa;
    }

    @Override
    @Transactional
    public RunbookExecution save(RunbookExecution execution) {
        RunbookExecutionEntity entity = new RunbookExecutionEntity();
        entity.setId(execution.id());
        entity.setRunbookId(execution.runbookId());
        entity.setStartedAt(execution.startedAt());
        entity.setFinishedAt(execution.finishedAt());
        entity.setStatus(execution.status());
        RunbookExecutionEntity saved = executionJpa.save(entity);
        resultJpa.deleteByExecutionId(saved.getId());
        List<RunbookStepResultEntity> rows = execution.stepResults().stream()
                .map(result -> {
                    RunbookStepResultEntity row = new RunbookStepResultEntity();
                    row.setExecutionId(saved.getId());
                    row.setSeq(result.seq());
                    row.setCommand(result.command());
                    row.setStatus(result.status());
                    row.setOutput(result.output());
                    return row;
                })
                .toList();
        resultJpa.saveAll(rows);
        return toDomain(saved);
    }

    @Override
    public Optional<RunbookExecution> findById(long id) {
        return executionJpa.findById(id).map(this::toDomain);
    }

    @Override
    public List<RunbookExecution> findByRunbookId(long runbookId) {
        List<RunbookExecutionEntity> entities =
                executionJpa.findTop100ByRunbookIdOrderByStartedAtDesc(runbookId);
        Map<Long, List<StepResult>> resultsByExecution = loadResultsBatch(
                entities.stream().map(RunbookExecutionEntity::getId).toList());
        return entities.stream()
                .map(e -> toDomain(e, resultsByExecution.getOrDefault(e.getId(), List.of())))
                .toList();
    }

    /** 批量取多次执行的步骤结果并按 executionId 分组（组内按 seq 升序），避免 N+1。 */
    private Map<Long, List<StepResult>> loadResultsBatch(List<Long> executionIds) {
        if (executionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<RunbookStepResultEntity>> grouped = new HashMap<>();
        for (RunbookStepResultEntity r : resultJpa.findByExecutionIdIn(executionIds)) {
            grouped.computeIfAbsent(r.getExecutionId(), k -> new ArrayList<>()).add(r);
        }
        Map<Long, List<StepResult>> result = new HashMap<>();
        grouped.forEach((id, rows) -> result.put(id, rows.stream()
                .sorted(Comparator.comparingInt(RunbookStepResultEntity::getSeq))
                .map(r -> new StepResult(r.getSeq(), r.getCommand(), r.getStatus(), r.getOutput()))
                .toList()));
        return result;
    }

    private RunbookExecution toDomain(RunbookExecutionEntity entity) {
        List<StepResult> results = resultJpa.findByExecutionIdOrderBySeqAsc(entity.getId()).stream()
                .map(r -> new StepResult(r.getSeq(), r.getCommand(), r.getStatus(), r.getOutput()))
                .toList();
        return toDomain(entity, results);
    }

    private RunbookExecution toDomain(RunbookExecutionEntity entity, List<StepResult> results) {
        return new RunbookExecution(entity.getId(), entity.getRunbookId(),
                entity.getStartedAt(), entity.getFinishedAt(), entity.getStatus(), results);
    }
}
