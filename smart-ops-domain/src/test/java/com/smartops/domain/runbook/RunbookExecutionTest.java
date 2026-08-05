package com.smartops.domain.runbook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RunbookExecution} 与 {@link StepResult} 领域模型测试。
 *
 * @author smartops
 * @since 1.0.0
 */
class RunbookExecutionTest {

    private static final Instant T1 = Instant.parse("2026-07-28T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-07-28T10:01:00Z");

    @Test
    @DisplayName("start 创建 RUNNING 状态执行记录")
    void should_createRunningExecution_when_start() {
        RunbookExecution execution = RunbookExecution.start(7L, T1);

        assertThat(execution.id()).isNull();
        assertThat(execution.runbookId()).isEqualTo(7L);
        assertThat(execution.startedAt()).isEqualTo(T1);
        assertThat(execution.finishedAt()).isNull();
        assertThat(execution.status()).isEqualTo("RUNNING");
        assertThat(execution.stepResults()).isEmpty();
    }

    @Test
    @DisplayName("finish 保留 id 与 startedAt，写入终态")
    void should_finishExecution_when_finish() {
        RunbookExecution running = new RunbookExecution(3L, 7L, T1, null, "RUNNING", List.of());
        List<StepResult> results = List.of(new StepResult(1, "HTTP GET x", "SUCCESS", "200"));

        RunbookExecution finished = running.finish("SUCCESS", results, T2);

        assertThat(finished.id()).isEqualTo(3L);
        assertThat(finished.runbookId()).isEqualTo(7L);
        assertThat(finished.startedAt()).isEqualTo(T1);
        assertThat(finished.finishedAt()).isEqualTo(T2);
        assertThat(finished.status()).isEqualTo("SUCCESS");
        assertThat(finished.stepResults()).hasSize(1);
        assertThat(finished.stepResults().get(0).command()).isEqualTo("HTTP GET x");
    }

    @Test
    @DisplayName("stepResults 为 null 时归一化为空列表")
    void should_normalizeNullResults() {
        RunbookExecution execution = new RunbookExecution(null, 1L, T1, null, "RUNNING", null);
        assertThat(execution.stepResults()).isEmpty();
    }
}
