package com.smartops.domain.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link MetricSample} 单元测试。
 *
 * @author smartops
 * @since 1.0.0
 */
class MetricSampleTest {

    @Test
    @DisplayName("labels 为 null 时归一化为空串")
    void should_defaultLabels_when_null() {
        MetricSample sample = new MetricSample(1L, "cpu", null, 0.5, Instant.now());
        assertThat(sample.labels()).isEmpty();
        assertThat(sample.value()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("metricName 为 null 时抛出异常")
    void should_throw_when_metricNameNull() {
        assertThatThrownBy(() -> new MetricSample(null, null, "a", 1.0, Instant.now()))
                .isInstanceOf(NullPointerException.class);
    }
}
