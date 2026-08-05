package com.smartops.infrastructure.persistence.runbook.impl;

import com.smartops.domain.runbook.Runbook;
import com.smartops.domain.runbook.port.RunbookRepository;
import com.smartops.infrastructure.persistence.runbook.RunbookEntity;
import com.smartops.infrastructure.persistence.runbook.RunbookJpaRepository;
import com.smartops.infrastructure.persistence.runbook.RunbookStepEntity;
import com.smartops.infrastructure.persistence.runbook.RunbookStepJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runbook 定义持久化端口的 JPA 实现。
 *
 * <p>保存时整体替换步骤列表；步骤类型由指令前缀推导
 * （{@code HTTP }/{@code WEBHOOK } 前缀，否则 LLM）。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Repository
public class RunbookRepositoryImpl implements RunbookRepository {

    private final RunbookJpaRepository runbookJpa;
    private final RunbookStepJpaRepository stepJpa;

    /**
     * 构造 Runbook 持久化实现。
     *
     * @param runbookJpa Runbook 主表仓库
     * @param stepJpa    步骤表仓库
     */
    public RunbookRepositoryImpl(RunbookJpaRepository runbookJpa, RunbookStepJpaRepository stepJpa) {
        this.runbookJpa = runbookJpa;
        this.stepJpa = stepJpa;
    }

    @Override
    @Transactional
    public Runbook save(Runbook runbook) {
        RunbookEntity entity = toEntity(runbook);
        RunbookEntity saved = runbookJpa.save(entity);
        stepJpa.deleteByRunbookId(saved.getId());
        List<String> steps = runbook.steps();
        for (int i = 0; i < steps.size(); i++) {
            RunbookStepEntity step = new RunbookStepEntity();
            step.setRunbookId(saved.getId());
            step.setSeq(i + 1);
            step.setStepType(deriveType(steps.get(i)));
            step.setConfigJson(steps.get(i));
            stepJpa.save(step);
        }
        return toDomain(saved, steps);
    }

    @Override
    public Optional<Runbook> findById(long id) {
        return runbookJpa.findById(id).map(e -> toDomain(e, loadSteps(id)));
    }

    @Override
    public List<Runbook> findAll() {
        List<RunbookEntity> entities = runbookJpa.findAll();
        Map<Long, List<String>> stepsByRunbook = loadStepsBatch(
                entities.stream().map(RunbookEntity::getId).toList());
        return entities.stream()
                .map(e -> toDomain(e, stepsByRunbook.getOrDefault(e.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional
    public void deleteById(long id) {
        stepJpa.deleteByRunbookId(id);
        runbookJpa.deleteById(id);
    }

    private List<String> loadSteps(long runbookId) {
        return stepJpa.findByRunbookIdOrderBySeqAsc(runbookId).stream()
                .map(RunbookStepEntity::getConfigJson)
                .toList();
    }

    /** 批量取全部 Runbook 的步骤并按 runbookId 分组（组内按 seq 升序），避免 N+1。 */
    private Map<Long, List<String>> loadStepsBatch(List<Long> runbookIds) {
        if (runbookIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<RunbookStepEntity>> grouped = new HashMap<>();
        for (RunbookStepEntity step : stepJpa.findByRunbookIdIn(runbookIds)) {
            grouped.computeIfAbsent(step.getRunbookId(), k -> new ArrayList<>()).add(step);
        }
        Map<Long, List<String>> result = new HashMap<>();
        grouped.forEach((id, steps) -> result.put(id, steps.stream()
                .sorted(Comparator.comparingInt(RunbookStepEntity::getSeq))
                .map(RunbookStepEntity::getConfigJson)
                .toList()));
        return result;
    }

    private static String deriveType(String step) {
        if (step.startsWith("HTTP ")) {
            return "HTTP";
        }
        if (step.startsWith("WEBHOOK ")) {
            return "WEBHOOK";
        }
        return "LLM";
    }

    private RunbookEntity toEntity(Runbook runbook) {
        RunbookEntity entity = new RunbookEntity();
        entity.setId(runbook.id());
        entity.setName(runbook.name());
        entity.setDescription(runbook.description());
        entity.setTriggerKeyword(runbook.triggerKeyword());
        entity.setSafetyLevel(runbook.safetyLevel());
        entity.setRollbackStepsJson(runbook.rollbackSteps());
        entity.setEnabled(runbook.enabled());
        entity.setCreatedAt(Instant.now());
        return entity;
    }

    private Runbook toDomain(RunbookEntity entity, List<String> steps) {
        return new Runbook(entity.getId(), entity.getName(), entity.getDescription(),
                entity.getTriggerKeyword(), steps, entity.getSafetyLevel(),
                entity.getRollbackStepsJson(), entity.isEnabled());
    }
}
