package com.smartops.agent.logwatch.pipeline.impl;

import com.smartops.common.enums.AlertLevel;
import com.smartops.domain.logwatch.AnalysisContext;
import com.smartops.domain.logwatch.AnalysisOutcome;
import com.smartops.domain.logwatch.ClassificationResult;
import com.smartops.domain.logwatch.LogEvent;
import com.smartops.domain.logwatch.port.LogLevelClassifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MlClassifyLayer} 单元测试。
 *
 * <p>覆盖全部分支：已定级直通（只升不降）、分类器缺失/未就绪抑制、
 * 推理异常抑制、低置信抑制、判 INFO 抑制、高置信 ERROR/WARN 救援并计数。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class MlClassifyLayerTest {

    private static final double THRESHOLD = 0.85;

    private AnalysisContext deferredCtx(String content) {
        return new AnalysisContext(new LogEvent("app.log", content, Instant.now()));
    }

    private LogLevelClassifier stub(boolean ready, ClassificationResult result) {
        return new LogLevelClassifier() {
            @Override
            public ClassificationResult classify(String content) {
                return result;
            }

            @Override
            public boolean isReady() {
                return ready;
            }
        };
    }

    @Test
    @DisplayName("层级 order 为 2")
    void should_orderTwo_when_orderCalled() {
        assertThat(new MlClassifyLayer(null, THRESHOLD).order()).isEqualTo(2);
    }

    @Test
    @DisplayName("L1 已定级的事件直接放行且不改判（只升不降）")
    void should_proceedUntouched_when_levelAlreadySet() {
        MlClassifyLayer layer = new MlClassifyLayer(
                stub(true, new ClassificationResult(AlertLevel.ERROR, 0.99)), THRESHOLD);
        AnalysisContext ctx = deferredCtx("connection refused");
        ctx.setLevel(AlertLevel.WARN);

        AnalysisOutcome outcome = layer.apply(ctx);

        assertThat(outcome.verdict()).isEqualTo(AnalysisOutcome.Verdict.PROCEED);
        assertThat(ctx.getLevel()).isEqualTo(AlertLevel.WARN);
        assertThat(ctx.getLayerReached()).isZero();
        assertThat(layer.getRescuedCount()).isZero();
    }

    @Test
    @DisplayName("分类器为 null 时抑制待定事件（与旧 L1 行为一致）")
    void should_suppress_when_classifierAbsent() {
        MlClassifyLayer layer = new MlClassifyLayer(null, THRESHOLD);

        AnalysisOutcome outcome = layer.apply(deferredCtx("connection refused"));

        assertThat(outcome.verdict()).isEqualTo(AnalysisOutcome.Verdict.SUPPRESS);
    }

    @Test
    @DisplayName("分类器未就绪（准确率门禁不达标）时抑制")
    void should_suppress_when_classifierNotReady() {
        MlClassifyLayer layer = new MlClassifyLayer(
                stub(false, new ClassificationResult(AlertLevel.ERROR, 0.99)), THRESHOLD);

        AnalysisOutcome outcome = layer.apply(deferredCtx("connection refused"));

        assertThat(outcome.verdict()).isEqualTo(AnalysisOutcome.Verdict.SUPPRESS);
    }

    @Test
    @DisplayName("推理抛异常时抑制（端口弃权契约被破坏的兜底）")
    void should_suppress_when_classifyThrows() {
        LogLevelClassifier broken = new LogLevelClassifier() {
            @Override
            public ClassificationResult classify(String content) {
                throw new RuntimeException("model broken");
            }

            @Override
            public boolean isReady() {
                return true;
            }
        };
        MlClassifyLayer layer = new MlClassifyLayer(broken, THRESHOLD);

        AnalysisOutcome outcome = layer.apply(deferredCtx("connection refused"));

        assertThat(outcome.verdict()).isEqualTo(AnalysisOutcome.Verdict.SUPPRESS);
    }

    @Test
    @DisplayName("置信度低于阈值时抑制")
    void should_suppress_when_confidenceBelowThreshold() {
        MlClassifyLayer layer = new MlClassifyLayer(
                stub(true, new ClassificationResult(AlertLevel.ERROR, 0.5)), THRESHOLD);

        AnalysisOutcome outcome = layer.apply(deferredCtx("connection refused"));

        assertThat(outcome.verdict()).isEqualTo(AnalysisOutcome.Verdict.SUPPRESS);
    }

    @Test
    @DisplayName("高置信判为 INFO 时抑制（与旧行为一致，不产生告警）")
    void should_suppress_when_predictedInfo() {
        MlClassifyLayer layer = new MlClassifyLayer(
                stub(true, new ClassificationResult(AlertLevel.INFO, 0.97)), THRESHOLD);

        AnalysisOutcome outcome = layer.apply(deferredCtx("user login success"));

        assertThat(outcome.verdict()).isEqualTo(AnalysisOutcome.Verdict.SUPPRESS);
    }

    @Test
    @DisplayName("高置信判为 ERROR 时写入级别放行并计救援数")
    void should_rescue_when_confidentError() {
        MlClassifyLayer layer = new MlClassifyLayer(
                stub(true, new ClassificationResult(AlertLevel.ERROR, 0.93)), THRESHOLD);
        AnalysisContext ctx = deferredCtx("connection refused by db-01");

        AnalysisOutcome outcome = layer.apply(ctx);

        assertThat(outcome.verdict()).isEqualTo(AnalysisOutcome.Verdict.PROCEED);
        assertThat(ctx.getLevel()).isEqualTo(AlertLevel.ERROR);
        assertThat(ctx.getLayerReached()).isEqualTo(2);
        assertThat(layer.getRescuedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("高置信判为 WARN 时写入级别放行并累计救援数")
    void should_rescue_when_confidentWarn() {
        MlClassifyLayer layer = new MlClassifyLayer(
                stub(true, new ClassificationResult(AlertLevel.WARN, 0.88)), THRESHOLD);

        layer.apply(deferredCtx("latency p99 above baseline"));
        layer.apply(deferredCtx("replica lag growing"));

        assertThat(layer.getRescuedCount()).isEqualTo(2);
    }
}
