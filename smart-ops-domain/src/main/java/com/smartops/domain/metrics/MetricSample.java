package com.smartops.domain.metrics;

import java.time.Instant;
import java.util.Objects;

/**
 * 指标采样点（阶段八 Prometheus 指标原生采集）。
 */
public record MetricSample(
        Long id, String metricName, String labels, double value, Instant timestamp
) {
    public MetricSample {
        Objects.requireNonNull(metricName);
        if (labels == null) labels = "";
    }
}
