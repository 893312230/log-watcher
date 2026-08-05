package com.smartops.infrastructure.metrics;

import com.smartops.domain.metrics.MetricSample;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PrometheusMetricCollector} 单元测试（file: 协议喂 exposition 报文，无网络依赖）。
 *
 * @author smartops
 * @since 1.0.0
 */
class PrometheusMetricCollectorTest {

    @TempDir
    Path tempDir;

    private PrometheusMetricCollector collectorFor(String content) throws Exception {
        Path file = tempDir.resolve("metrics.txt");
        Files.writeString(file, content);
        return new PrometheusMetricCollector(file.toUri().toURL().toString());
    }

    @Test
    @DisplayName("解析 exposition：跳过注释与不关注指标，保留关注指标")
    void should_parseInterestedMetrics() throws Exception {
        String exposition = """
                # HELP jvm_memory_used_bytes memory
                # TYPE jvm_memory_used_bytes gauge
                jvm_memory_used_bytes{area="heap"} 1.5e8
                unrelated_metric_total 42
                process_cpu_usage 0.25
                非法行没有匹配格式
                smartops_alerts_total{level="ERROR"} 3
                """;
        List<MetricSample> samples = collectorFor(exposition).collect();

        assertThat(samples).hasSize(3);
        assertThat(samples.get(0).metricName()).isEqualTo("jvm_memory_used_bytes");
        assertThat(samples.get(0).labels()).isEqualTo("area=\"heap\"");
        assertThat(samples.get(0).value()).isEqualTo(1.5e8);
        assertThat(samples.get(1).metricName()).isEqualTo("process_cpu_usage");
        assertThat(samples.get(2).metricName()).isEqualTo("smartops_alerts_total");
    }

    @Test
    @DisplayName("URL 不可读 → 返回空列表不抛异常")
    void should_returnEmpty_when_urlUnreadable() {
        PrometheusMetricCollector collector =
                new PrometheusMetricCollector("file:/nonexistent/path/metrics.txt");

        assertThat(collector.collect()).isEmpty();
    }

    @Test
    @DisplayName("空文件 → 空列表")
    void should_returnEmpty_when_fileEmpty() throws Exception {
        assertThat(collectorFor("").collect()).isEmpty();
    }

    @Test
    @DisplayName("无标签指标 → labels 为空串")
    void should_parseMetricWithoutLabels() throws Exception {
        List<MetricSample> samples = collectorFor("system_cpu_usage 0.5\n").collect();

        assertThat(samples).hasSize(1);
        assertThat(samples.get(0).labels()).isEmpty();
        assertThat(samples.get(0).value()).isEqualTo(0.5);
    }
}
