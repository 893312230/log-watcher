package com.smartops.agent.logwatch.pipeline.impl;

import com.smartops.agent.orchestrator.SupervisorAgent;
import com.smartops.common.enums.AgentMode;
import com.smartops.common.enums.AlertLevel;
import com.smartops.common.model.AgentExecutionResult;
import com.smartops.domain.logwatch.AnalysisContext;
import com.smartops.domain.logwatch.AnalysisOutcome;
import com.smartops.domain.logwatch.LogEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link L4SupervisorLayer} 单元测试。
 *
 * <p>覆盖：未升级跳过、会诊成功追加结论、会诊失败降级、异常降级、
 * 日上限与日界重置。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class L4SupervisorLayerTest {

    private static final int DAILY_LIMIT = 2;

    private SupervisorAgent supervisorAgent;
    private MutableClock clock;
    private L4SupervisorLayer layer;

    @BeforeEach
    void setUp() {
        supervisorAgent = mock(SupervisorAgent.class);
        clock = new MutableClock(Instant.parse("2026-07-22T10:00:00Z"));
        layer = new L4SupervisorLayer(supervisorAgent, DAILY_LIMIT, clock);
    }

    private AnalysisContext ctxOf(boolean escalate) {
        AnalysisContext ctx = new AnalysisContext(
                new LogEvent("app.log", "ERROR db timeout", Instant.now()));
        ctx.setLevel(AlertLevel.ERROR);
        ctx.setAnalysis("L3 初步结论");
        ctx.setSuggestion("L3 建议");
        if (escalate) {
            ctx.markEscalate();
        }
        return ctx;
    }

    @Test
    @DisplayName("层级 order 为 5")
    void should_orderFour_when_orderCalled() {
        assertThat(layer.order()).isEqualTo(5);
    }

    @Test
    @DisplayName("未标记升级时不调 Supervisor 直接完成")
    void should_completeWithoutSupervisor_when_notEscalated() {
        AnalysisOutcome outcome = layer.apply(ctxOf(false));

        assertThat(outcome.verdict()).isEqualTo(AnalysisOutcome.Verdict.COMPLETE);
        verify(supervisorAgent, never()).orchestrate(anyString(), anyString());
    }

    @Test
    @DisplayName("升级且会诊成功时追加会诊结论")
    void should_appendConsultResult_when_supervisorSucceeds() {
        when(supervisorAgent.orchestrate(anyString(), anyString()))
                .thenReturn(AgentExecutionResult.success(
                        "根因为连接池泄漏", AgentMode.PLAN_AND_SOLVE, 3, List.of("step1")));
        AnalysisContext ctx = ctxOf(true);

        AnalysisOutcome outcome = layer.apply(ctx);

        assertThat(outcome.verdict()).isEqualTo(AnalysisOutcome.Verdict.COMPLETE);
        assertThat(ctx.getAnalysis()).contains("L3 初步结论").contains("【多 Agent 会诊】")
                .contains("根因为连接池泄漏");
        assertThat(ctx.getLayerReached()).isEqualTo(5);

        ArgumentCaptor<String> conversationId = ArgumentCaptor.forClass(String.class);
        verify(supervisorAgent).orchestrate(anyString(), conversationId.capture());
        assertThat(conversationId.getValue()).startsWith("logwatch-");
    }

    @Test
    @DisplayName("会诊执行失败时保留 L3 结论并标注降级")
    void should_keepL3Result_when_supervisorFails() {
        when(supervisorAgent.orchestrate(anyString(), anyString()))
                .thenReturn(AgentExecutionResult.failure(
                        AgentMode.PLAN_AND_SOLVE, 1, List.of(), "全部子任务失败"));
        AnalysisContext ctx = ctxOf(true);

        layer.apply(ctx);

        assertThat(ctx.getAnalysis()).contains("L3 初步结论")
                .contains("【会诊降级】").contains("全部子任务失败");
    }

    @Test
    @DisplayName("会诊调用抛异常时保留 L3 结论并标注降级")
    void should_keepL3Result_when_supervisorThrows() {
        when(supervisorAgent.orchestrate(anyString(), anyString()))
                .thenThrow(new IllegalStateException("worker timeout"));
        AnalysisContext ctx = ctxOf(true);

        AnalysisOutcome outcome = layer.apply(ctx);

        assertThat(outcome.verdict()).isEqualTo(AnalysisOutcome.Verdict.COMPLETE);
        assertThat(ctx.getAnalysis()).contains("【会诊降级】");
    }

    @Test
    @DisplayName("超过每日会诊上限后不再调用 Supervisor")
    void should_stopCallingSupervisor_when_dailyLimitExceeded() {
        when(supervisorAgent.orchestrate(anyString(), anyString()))
                .thenReturn(AgentExecutionResult.success(
                        "ok", AgentMode.PLAN_AND_SOLVE, 1, List.of()));

        layer.apply(ctxOf(true));
        layer.apply(ctxOf(true));
        layer.apply(ctxOf(true)); // 超限

        verify(supervisorAgent, org.mockito.Mockito.times(DAILY_LIMIT))
                .orchestrate(anyString(), anyString());
    }

    @Test
    @DisplayName("跨日后日计数自动清零恢复会诊")
    void should_resetDailyCount_when_dateChanges() {
        when(supervisorAgent.orchestrate(anyString(), anyString()))
                .thenReturn(AgentExecutionResult.success(
                        "ok", AgentMode.PLAN_AND_SOLVE, 1, List.of()));

        layer.apply(ctxOf(true));
        layer.apply(ctxOf(true));
        clock.advance(Duration.ofDays(1));
        layer.apply(ctxOf(true)); // 新一天，恢复

        verify(supervisorAgent, org.mockito.Mockito.times(3))
                .orchestrate(anyString(), anyString());
    }

    /** 可推进的测试时钟。 */
    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
