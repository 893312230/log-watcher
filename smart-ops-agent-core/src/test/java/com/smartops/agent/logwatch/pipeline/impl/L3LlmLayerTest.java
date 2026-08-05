package com.smartops.agent.logwatch.pipeline.impl;

import com.smartops.common.enums.AlertLevel;
import com.smartops.common.exception.LlmCallException;
import com.smartops.domain.logwatch.AnalysisContext;
import com.smartops.domain.logwatch.AnalysisOutcome;
import com.smartops.domain.logwatch.LogEvent;
import com.smartops.infrastructure.chat.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link L3LlmLayer} 单元测试。
 *
 * <p>覆盖：正常分析、需会诊升级、高频重复升级、LLM 异常降级、
 * 分钟级限流降级、非结构化响应兜底、知识参考注入。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class L3LlmLayerTest {

    private static final String SYSTEM_PROMPT = "你是运维专家，输出【原因分析】与【解决建议】";
    private static final int RATE_PER_MINUTE = 2;
    private static final int ESCALATE_OCCURRENCE = 3;

    private ChatService chatService;
    private Clock clock;
    private L3LlmLayer layer;

    @BeforeEach
    void setUp() {
        chatService = mock(ChatService.class);
        clock = Clock.fixed(Instant.parse("2026-07-22T10:00:00Z"), ZoneOffset.UTC);
        layer = new L3LlmLayer(chatService, SYSTEM_PROMPT, RATE_PER_MINUTE, ESCALATE_OCCURRENCE, clock);
    }

    private AnalysisContext ctxOf(String content) {
        AnalysisContext ctx = new AnalysisContext(new LogEvent("app.log", content, Instant.now()));
        ctx.setLevel(AlertLevel.ERROR);
        return ctx;
    }

    @Test
    @DisplayName("层级 order 为 4")
    void should_orderThree_when_orderCalled() {
        assertThat(layer.order()).isEqualTo(4);
    }

    @Test
    @DisplayName("LLM 正常返回结构化结论时解析分析与建议并终止管线")
    void should_parseAnalysisAndSuggestion_when_llmResponds() {
        when(chatService.chatWithSystemPrompt(anyString(), anyString()))
                .thenReturn("【原因分析】连接池耗尽导致超时\n【解决建议】调大连接池并排查慢查询");
        AnalysisContext ctx = ctxOf("ERROR db timeout");

        AnalysisOutcome outcome = layer.apply(ctx);

        assertThat(outcome.verdict()).isEqualTo(AnalysisOutcome.Verdict.COMPLETE);
        assertThat(ctx.getAnalysis()).isEqualTo("连接池耗尽导致超时");
        assertThat(ctx.getSuggestion()).isEqualTo("调大连接池并排查慢查询");
        assertThat(ctx.isEscalate()).isFalse();
        assertThat(ctx.getLayerReached()).isEqualTo(4);
    }

    @Test
    @DisplayName("LLM 输出需会诊标记时升级 Supervisor")
    void should_escalate_when_llmMarksEscalation() {
        when(chatService.chatWithSystemPrompt(anyString(), anyString()))
                .thenReturn("【原因分析】疑似级联故障\n【解决建议】多服务联合排查\n【需会诊】");
        AnalysisContext ctx = ctxOf("ERROR downstream unavailable");

        AnalysisOutcome outcome = layer.apply(ctx);

        assertThat(outcome.verdict()).isEqualTo(AnalysisOutcome.Verdict.PROCEED);
        assertThat(ctx.isEscalate()).isTrue();
        assertThat(ctx.getSuggestion()).isEqualTo("多服务联合排查");
    }

    @Test
    @DisplayName("同指纹合并次数达到阈值时升级 Supervisor")
    void should_escalate_when_occurrenceReachesThreshold() {
        when(chatService.chatWithSystemPrompt(anyString(), anyString()))
                .thenReturn("【原因分析】反复超时\n【解决建议】观察");
        AnalysisContext ctx = ctxOf("ERROR db timeout");
        ctx.incrementOccurrence(ESCALATE_OCCURRENCE - 1);

        AnalysisOutcome outcome = layer.apply(ctx);

        assertThat(outcome.verdict()).isEqualTo(AnalysisOutcome.Verdict.PROCEED);
        assertThat(ctx.isEscalate()).isTrue();
    }

    @Test
    @DisplayName("LLM 调用异常时以规则结果降级落库")
    void should_degradeWithRuleResult_when_llmThrows() {
        when(chatService.chatWithSystemPrompt(anyString(), anyString()))
                .thenThrow(new LlmCallException("HTTP 503"));
        AnalysisContext ctx = ctxOf("ERROR db timeout\n\tat com.x.Dao.query(Dao.java:1)");

        AnalysisOutcome outcome = layer.apply(ctx);

        assertThat(outcome.verdict()).isEqualTo(AnalysisOutcome.Verdict.COMPLETE);
        assertThat(ctx.getAnalysis()).contains("降级").contains("ERROR db timeout");
        assertThat(ctx.getSuggestion()).contains("人工");
    }

    @Test
    @DisplayName("超过每分钟限流时不调 LLM 直接降级")
    void should_degradeWithoutLlmCall_when_rateLimited() {
        when(chatService.chatWithSystemPrompt(anyString(), anyString()))
                .thenReturn("【原因分析】x\n【解决建议】y");

        layer.apply(ctxOf("ERROR one"));
        layer.apply(ctxOf("ERROR two"));
        AnalysisOutcome third = layer.apply(ctxOf("ERROR three"));

        assertThat(third.verdict()).isEqualTo(AnalysisOutcome.Verdict.COMPLETE);
        verify(chatService, org.mockito.Mockito.times(2))
                .chatWithSystemPrompt(anyString(), anyString());
    }

    @Test
    @DisplayName("LLM 响应无结构标记时整体作为分析结论")
    void should_useWholeResponseAsAnalysis_when_noMarkers() {
        when(chatService.chatWithSystemPrompt(anyString(), anyString()))
                .thenReturn("这看起来是磁盘写满导致的连锁失败");
        AnalysisContext ctx = ctxOf("ERROR write failed");

        layer.apply(ctx);

        assertThat(ctx.getAnalysis()).isEqualTo("这看起来是磁盘写满导致的连锁失败");
        assertThat(ctx.getSuggestion()).isEmpty();
    }

    @Test
    @DisplayName("L2 知识参考被注入 LLM 用户消息")
    void should_injectKnowledgeRefs_when_present() {
        when(chatService.chatWithSystemPrompt(anyString(), anyString()))
                .thenReturn("【原因分析】x\n【解决建议】y");
        AnalysisContext ctx = ctxOf("ERROR db timeout");
        ctx.addKnowledgeRef("runbooks/db-timeout.md");

        layer.apply(ctx);

        ArgumentCaptor<String> userMessage = ArgumentCaptor.forClass(String.class);
        verify(chatService).chatWithSystemPrompt(anyString(), userMessage.capture());
        assertThat(userMessage.getValue()).contains("runbooks/db-timeout.md");
    }

    @Test
    @DisplayName("限流窗口滑动后恢复 LLM 调用")
    void should_resumeAfterWindowSlides_when_timePasses() {
        MutableClock mutableClock = new MutableClock(Instant.parse("2026-07-22T10:00:00Z"));
        L3LlmLayer sliding = new L3LlmLayer(chatService, SYSTEM_PROMPT,
                RATE_PER_MINUTE, ESCALATE_OCCURRENCE, mutableClock);
        when(chatService.chatWithSystemPrompt(anyString(), anyString()))
                .thenReturn("【原因分析】x\n【解决建议】y");

        sliding.apply(ctxOf("ERROR one"));
        sliding.apply(ctxOf("ERROR two"));
        sliding.apply(ctxOf("ERROR three")); // 限流降级，不调 LLM

        mutableClock.advance(java.time.Duration.ofSeconds(61));
        sliding.apply(ctxOf("ERROR four")); // 窗口过期，恢复调用

        verify(chatService, org.mockito.Mockito.times(3))
                .chatWithSystemPrompt(anyString(), anyString());
    }

    /** 可推进的测试时钟。 */
    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(java.time.Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
