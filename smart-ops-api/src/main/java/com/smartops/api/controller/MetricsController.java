package com.smartops.api.controller;

import com.smartops.domain.metrics.MetricSample;
import com.smartops.infrastructure.metrics.PrometheusMetricCollector;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final List<MetricSample> buffer = new CopyOnWriteArrayList<>();
    private final PrometheusMetricCollector collector;

    public MetricsController(ObjectProvider<PrometheusMetricCollector> provider) {
        this.collector = provider.getIfAvailable();
    }

    /** 手动触发一次采集并返回结果。 */
    @PostMapping("/collect")
    public Map<String, Object> collect() {
        if (collector == null) return Map.of("status", "no collector configured");
        List<MetricSample> samples = collector.collect();
        buffer.addAll(samples);
        return Map.of("status", "collected", "count", samples.size());
    }

    /** 查询指标时序数据。 */
    @GetMapping
    public Map<String, Object> query(
            @RequestParam(defaultValue = "smartops_llm_calls_seconds_count") String metric,
            @RequestParam(defaultValue = "24") int hours) {
        long cutoff = System.currentTimeMillis() - hours * 3600000L;
        List<MetricSample> filtered = buffer.stream()
                .filter(s -> s.metricName().equals(metric)
                        && s.timestamp().toEpochMilli() > cutoff)
                .sorted(Comparator.comparing(MetricSample::timestamp))
                .collect(Collectors.toList());
        return Map.of("metric", metric, "hours", hours, "samples", filtered);
    }

    /** 列出所有已采集的指标名称。 */
    @GetMapping("/names")
    public List<String> names() {
        return buffer.stream().map(MetricSample::metricName).distinct().sorted().toList();
    }
}
