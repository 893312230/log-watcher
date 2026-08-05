package com.smartops.agent.logwatch.anomaly;

import com.smartops.domain.logwatch.LogEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link StatisticalBaselineDetector} 单元测试。
 *
 * @author smartops
 * @since 1.0.0
 */
class StatisticalBaselineDetectorTest {

    private LogEvent event(String source) {
        return new LogEvent(source, "ERROR x", Instant.now());
    }

    @Test
    @DisplayName("评分低于阈值 → 返回 0")
    void should_returnZero_when_deviationBelowThreshold() {
        // 评分上限为 1.0，阈值高于 1.0 时必然落入 below-threshold 分支
        StatisticalBaselineDetector detector = new StatisticalBaselineDetector(1.1);

        assertThat(detector.score(event("app.log"))).isZero();
    }

    @Test
    @DisplayName("阈值为 0 时任何非零评分都透出")
    void should_returnScore_when_thresholdZero() throws Exception {
        StatisticalBaselineDetector detector = new StatisticalBaselineDetector(0.0);
        detector.score(event("app.log"));
        Thread.sleep(50);

        double score = detector.score(event("app.log"));

        assertThat(score).isGreaterThanOrEqualTo(0.0).isLessThanOrEqualTo(1.0);
    }

    @Test
    @DisplayName("threshold() 返回构造阈值")
    void should_exposeThreshold() {
        assertThat(new StatisticalBaselineDetector(0.7).threshold()).isEqualTo(0.7);
    }

    @Test
    @DisplayName("snapshots 按来源返回基线快照")
    void should_snapshotBaselinesPerSource() {
        StatisticalBaselineDetector detector = new StatisticalBaselineDetector(0.8);
        detector.score(event("a.log"));
        detector.score(event("b.log"));

        assertThat(detector.snapshots())
                .containsKeys("a.log", "b.log");
        assertThat(detector.snapshots().get("a.log").meanMs()).isPositive();
        assertThat(detector.snapshots().get("a.log").rmsMs()).isPositive();
        assertThat(detector.snapshots().get("a.log").lastTimestampMs()).isPositive();
    }
}
