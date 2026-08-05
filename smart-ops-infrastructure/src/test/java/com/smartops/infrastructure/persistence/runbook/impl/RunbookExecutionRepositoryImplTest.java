package com.smartops.infrastructure.persistence.runbook.impl;

import com.smartops.domain.runbook.RunbookExecution;
import com.smartops.domain.runbook.StepResult;
import com.smartops.infrastructure.persistence.runbook.RunbookExecutionJpaRepository;
import com.smartops.infrastructure.persistence.runbook.RunbookStepResultJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RunbookExecutionRepositoryImpl} 数据层集成测试（@DataJpaTest + H2）。
 *
 * @author smartops
 * @since 1.0.0
 */
@DataJpaTest
class RunbookExecutionRepositoryImplTest {

    private static final Instant T1 = Instant.parse("2026-07-28T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-07-28T11:00:00Z");

    @Autowired
    private RunbookExecutionJpaRepository executionJpa;

    @Autowired
    private RunbookStepResultJpaRepository resultJpa;

    private RunbookExecutionRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new RunbookExecutionRepositoryImpl(executionJpa, resultJpa);
    }

    @Test
    @DisplayName("保存执行记录含步骤结果，字段完整往返")
    void should_roundTripWithResults_when_saved() {
        RunbookExecution running = repository.save(RunbookExecution.start(9L, T1));
        assertThat(running.id()).isNotNull();

        RunbookExecution finished = running.finish("FAILED",
                List.of(new StepResult(1, "HTTP GET x", "SUCCESS", "200"),
                        new StepResult(2, "清理缓存", "FAILED", "timeout")), T2);
        repository.save(finished);

        Optional<RunbookExecution> found = repository.findById(running.id());
        assertThat(found).isPresent();
        assertThat(found.get().status()).isEqualTo("FAILED");
        assertThat(found.get().finishedAt()).isEqualTo(T2);
        assertThat(found.get().stepResults()).hasSize(2);
        assertThat(found.get().stepResults().get(1).output()).isEqualTo("timeout");
    }

    @Test
    @DisplayName("findByRunbookId 按开始时间倒序")
    void should_returnDescOrdered_when_findByRunbookId() {
        repository.save(RunbookExecution.start(9L, T1));
        repository.save(RunbookExecution.start(9L, T2));
        repository.save(RunbookExecution.start(99L, T2));

        List<RunbookExecution> history = repository.findByRunbookId(9L);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).startedAt()).isEqualTo(T2);
        assertThat(history.get(1).startedAt()).isEqualTo(T1);
    }

    @Test
    @DisplayName("findById 不存在返回空")
    void should_returnEmpty_when_notFound() {
        assertThat(repository.findById(12345L)).isEmpty();
    }
}
