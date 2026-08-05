package com.smartops.infrastructure.metrics;

import com.smartops.domain.metrics.MetricSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prometheus 指标采集器（阶段八）。
 *
 * <p>定时 scrape 本地 /actuator/prometheus 端点，
 * 解析 Prometheus exposition 格式为 MetricSample 列表。</p>
 */
public class PrometheusMetricCollector implements MetricCollector {

    private static final Logger log = LoggerFactory.getLogger(PrometheusMetricCollector.class);
    private static final Pattern METRIC_PATTERN =
            Pattern.compile("^(\\w+)\\{?([^}]*)\\}?\\s+([\\d.eE+-]+)");

    private final String prometheusUrl;

    /** 关注的指标前缀列表（过滤噪音）。 */
    private static final String[] INTERESTED_PREFIXES = {
            "jvm_memory_used", "jvm_gc_pause", "jvm_threads_",
            "process_cpu_usage", "system_cpu_usage",
            "http_server_requests_seconds", "smartops_"
    };

    public PrometheusMetricCollector(String prometheusUrl) {
        this.prometheusUrl = prometheusUrl;
    }

    @Override
    public List<MetricSample> collect() {
        List<MetricSample> samples = new ArrayList<>();
        try {
            URL url = URI.create(prometheusUrl).toURL();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(url.openStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("#")) continue;
                    Matcher m = METRIC_PATTERN.matcher(line);
                    if (m.find()) {
                        String name = m.group(1);
                        if (!isInterested(name)) continue;
                        double value = Double.parseDouble(m.group(3));
                        samples.add(new MetricSample(null, name,
                                m.group(2), value, Instant.now()));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("指标采集失败: {}", e.toString());
        }
        return samples;
    }

    private boolean isInterested(String name) {
        for (String prefix : INTERESTED_PREFIXES) {
            if (name.startsWith(prefix)) return true;
        }
        return false;
    }
}
