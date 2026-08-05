package com.smartops.agent.logwatch.pipeline.impl;

import com.smartops.domain.logwatch.AnalysisContext;
import com.smartops.domain.logwatch.AnalysisOutcome;
import com.smartops.domain.logwatch.LogEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link L0SuppressionLayer} 单元测试。
 *
 * <p>覆盖：首发放行、窗内抑制、窗后合并放行、异指纹放行、容量淘汰。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class L0SuppressionLayerTest {

    private static final Duration WINDOW = Duration.ofSeconds(300);
    private static final Instant T0 = Instant.parse("2026-07-22T10:00:00Z");

    /** 可手动推进的时钟。 */
    private AtomicLong nowMillis;
    private Clock clock;
    private L0SuppressionLayer layer;

    @BeforeEach
    void setUp() {
        nowMillis = new AtomicLong(T0.toEpochMilli());
        clock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return Instant.ofEpochMilli(nowMillis.get());
            }
        };
        layer = new L0SuppressionLayer(WINDOW, clock);
    }

    private AnalysisContext ctxOf(String content) {
        return new AnalysisContext(new LogEvent("app.log", content,
                Instant.ofEpochMilli(nowMillis.get())));
    }

    private void advanceSeconds(long seconds) {
        nowMillis.addAndGet(seconds * 1000);
    }

    @Test
    @DisplayName("层级 order 为 0")
    void should_orderZero_when_orderCalled() {
        assertThat(layer.order()).isZero();
    }

    @Test
    @DisplayName("首次出现的事件放行，发生次数为 1")
    void should_proceed_when_firstSeen() {
        AnalysisContext ctx = ctxOf("ERROR boom 1");

        AnalysisOutcome outcome = layer.apply(ctx);

        assertThat(outcome.verdict()).isEqualTo(AnalysisOutcome.Verdict.PROCEED);
        assertThat(ctx.getOccurrence()).isEqualTo(1);
        assertThat(ctx.getLayerReached()).isZero();
    }

    @Test
    @DisplayName("时间窗内同指纹事件被抑制并累计计数")
    void should_suppress_when_sameFingerprintWithinWindow() {
        layer.apply(ctxOf("ERROR boom 1001"));
        AnalysisContext second = ctxOf("ERROR boom 2002"); // 仅数字不同，指纹相同

        AnalysisOutcome outcome = layer.apply(second);

        assertThat(outcome.verdict()).isEqualTo(AnalysisOutcome.Verdict.SUPPRESS);
    }

    @Test
    @DisplayName("窗口过后再次出现：放行并合并窗口内计数")
    void should_proceedWithMergedCount_when_windowExpired() {
        layer.apply(ctxOf("ERROR boom 1001"));
        layer.apply(ctxOf("ERROR boom 2002")); // 抑制 ×1
        layer.apply(ctxOf("ERROR boom 3003")); // 抑制 ×2

        advanceSeconds(301);
        AnalysisContext fourth = ctxOf("ERROR boom 4004");
        AnalysisOutcome outcome = layer.apply(fourth);

        assertThat(outcome.verdict()).isEqualTo(AnalysisOutcome.Verdict.PROCEED);
        assertThat(fourth.getOccurrence()).isEqualTo(4);
    }

    @Test
    @DisplayName("不同指纹互不影响")
    void should_proceedIndependently_when_fingerprintDiffers() {
        layer.apply(ctxOf("ERROR boom 1001"));

        AnalysisContext other = ctxOf("ERROR disk full");
        AnalysisOutcome outcome = layer.apply(other);

        assertThat(outcome.verdict()).isEqualTo(AnalysisOutcome.Verdict.PROCEED);
        assertThat(other.getOccurrence()).isEqualTo(1);
    }

    @Test
    @DisplayName("条目数超容量时淘汰最旧窗口")
    void should_evictOldest_when_overCapacity() {
        L0SuppressionLayer tiny = new L0SuppressionLayer(WINDOW, clock, 2);

        tiny.apply(ctxOf("ERROR a 1"));
        advanceSeconds(10);
        tiny.apply(ctxOf("ERROR b 2"));
        advanceSeconds(10);
        // 第三个不同指纹触发淘汰（最旧的 a 被逐出）
        tiny.apply(ctxOf("ERROR c 3"));

        // a 的窗口已被淘汰：窗内再次出现应放行而非抑制
        AnalysisContext again = ctxOf("ERROR a 4");
        assertThat(tiny.apply(again).verdict()).isEqualTo(AnalysisOutcome.Verdict.PROCEED);
    }
}
