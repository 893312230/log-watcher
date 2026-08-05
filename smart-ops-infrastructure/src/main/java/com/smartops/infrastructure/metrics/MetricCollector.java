package com.smartops.infrastructure.metrics;

import com.smartops.domain.metrics.MetricSample;
import java.util.List;

/**
 * 指标采集器接口（阶段八 Prometheus 原生采集）。
 */
public interface MetricCollector {
    /** 执行一次采集，返回指标样本列表。 */
    List<MetricSample> collect();
}
