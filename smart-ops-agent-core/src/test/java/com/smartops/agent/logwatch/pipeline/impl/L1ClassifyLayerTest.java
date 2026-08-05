package com.smartops.agent.logwatch.pipeline.impl;

import com.smartops.common.enums.AlertLevel;
import com.smartops.domain.logwatch.AnalysisContext;
import com.smartops.domain.logwatch.AnalysisOutcome;
import com.smartops.domain.logwatch.LogEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link L1ClassifyLayer} 单元测试。
 *
 * <p>覆盖：ERROR/FATAL/Exception 定级、WARN 定级、INFO 丢弃、大小写不敏感。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class L1ClassifyLayerTest {

    private final L1ClassifyLayer layer = new L1ClassifyLayer();

    private AnalysisContext ctxOf(String content) {
        return new AnalysisContext(new LogEvent("app.log", content, Instant.now()));
    }

    @Test
    @DisplayName("层级 order 为 1")
    void should_orderOne_when_orderCalled() {
        assertThat(layer.order()).isEqualTo(1);
    }

    @Test
    @DisplayName("含 ERROR 的日志定级 ERROR 并放行")
    void should_classifyError_when_contentContainsError() {
        AnalysisContext ctx = ctxOf("2026-07-22 10:00:00 ERROR connection refused");

        AnalysisOutcome outcome = layer.apply(ctx);

        assertThat(outcome.verdict()).isEqualTo(AnalysisOutcome.Verdict.PROCEED);
        assertThat(ctx.getLevel()).isEqualTo(AlertLevel.ERROR);
        assertThat(ctx.getLayerReached()).isEqualTo(1);
    }

    @Test
    @DisplayName("含 Exception 的日志定级 ERROR")
    void should_classifyError_when_contentContainsException() {
        AnalysisContext ctx = ctxOf("java.lang.NullPointerException\n\tat com.x.Foo.bar(Foo.java:1)");

        layer.apply(ctx);

        assertThat(ctx.getLevel()).isEqualTo(AlertLevel.ERROR);
    }

    @Test
    @DisplayName("含 FATAL 的日志定级 ERROR")
    void should_classifyError_when_contentContainsFatal() {
        AnalysisContext ctx = ctxOf("2026-07-22 10:00:00 FATAL disk failure");

        layer.apply(ctx);

        assertThat(ctx.getLevel()).isEqualTo(AlertLevel.ERROR);
    }

    @Test
    @DisplayName("含 WARN 的日志定级 WARN 并放行")
    void should_classifyWarn_when_contentContainsWarn() {
        AnalysisContext ctx = ctxOf("2026-07-22 10:00:00 WARN slow query 2003ms");

        AnalysisOutcome outcome = layer.apply(ctx);

        assertThat(outcome.verdict()).isEqualTo(AnalysisOutcome.Verdict.PROCEED);
        assertThat(ctx.getLevel()).isEqualTo(AlertLevel.WARN);
    }

    @Test
    @DisplayName("普通 INFO 日志定级 INFO 并抑制丢弃")
    void should_suppressInfo_when_noErrorOrWarn() {
        AnalysisContext ctx = ctxOf("2026-07-22 10:00:00 INFO service started");

        AnalysisOutcome outcome = layer.apply(ctx);

        assertThat(outcome.verdict()).isEqualTo(AnalysisOutcome.Verdict.SUPPRESS);
        assertThat(ctx.getLevel()).isEqualTo(AlertLevel.INFO);
    }

    @Test
    @DisplayName("级别识别大小写不敏感")
    void should_beCaseInsensitive_when_matching() {
        AnalysisContext ctx = ctxOf("2026-07-22 10:00:00 error something failed");

        layer.apply(ctx);

        assertThat(ctx.getLevel()).isEqualTo(AlertLevel.ERROR);
    }

    @Test
    @DisplayName("defer 模式未命中正则时不定级放行（留 ML 层裁决）")
    void should_deferWithoutLevel_when_deferModeAndNoMatch() {
        L1ClassifyLayer deferLayer = new L1ClassifyLayer(true);
        AnalysisContext ctx = ctxOf("2026-07-22 10:00:00 INFO connection refused by db-01");

        AnalysisOutcome outcome = deferLayer.apply(ctx);

        assertThat(outcome.verdict()).isEqualTo(AnalysisOutcome.Verdict.PROCEED);
        assertThat(ctx.getLevel()).isNull();
        assertThat(ctx.getLayerReached()).isEqualTo(1);
    }

    @Test
    @DisplayName("defer 模式命中正则时行为与传统模式一致")
    void should_classifyNormally_when_deferModeAndRegexHit() {
        L1ClassifyLayer deferLayer = new L1ClassifyLayer(true);

        AnalysisContext errorCtx = ctxOf("ERROR something failed");
        AnalysisOutcome errorOutcome = deferLayer.apply(errorCtx);
        assertThat(errorOutcome.verdict()).isEqualTo(AnalysisOutcome.Verdict.PROCEED);
        assertThat(errorCtx.getLevel()).isEqualTo(AlertLevel.ERROR);

        AnalysisContext warnCtx = ctxOf("WARN slow query");
        AnalysisOutcome warnOutcome = deferLayer.apply(warnCtx);
        assertThat(warnOutcome.verdict()).isEqualTo(AnalysisOutcome.Verdict.PROCEED);
        assertThat(warnCtx.getLevel()).isEqualTo(AlertLevel.WARN);
    }
}
