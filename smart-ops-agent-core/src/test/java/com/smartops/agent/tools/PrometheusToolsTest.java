package com.smartops.agent.tools;

import com.smartops.infrastructure.metrics.PrometheusClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PrometheusTools} 单元测试。
 *
 * <p>验证工具的参数校验与端口委托契约：合法输入委托 {@link PrometheusClient}
 * 查询真实数据并透传结果（含 unavailable 降级响应），非法输入直接拒绝、
 * 不触发任何查询。PrometheusClient 一律 Mock，不连接真实 MCP/Prometheus。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class PrometheusToolsTest {

    private PrometheusClient prometheusClient;
    private PrometheusTools tools;

    @BeforeEach
    void setUp() {
        prometheusClient = mock(PrometheusClient.class);
        tools = new PrometheusTools(prometheusClient);
    }

    @Nested
    @DisplayName("queryMetric 方法")
    class QueryMetric {

        @ParameterizedTest
        @ValueSource(strings = {"cpu_usage", "memory_usage", "disk_usage", "qps"})
        @DisplayName("合法指标名称委托端口查询并透传结果")
        void should_delegateToClient_when_metricNameIsValid(String metricName) {
            when(prometheusClient.queryInstant(metricName))
                    .thenReturn("{\"metric\":\"" + metricName + "\",\"value\":42.0}");

            String result = tools.queryMetric(metricName);

            assertThat(result).contains("\"metric\":\"" + metricName + "\"");
            verify(prometheusClient).queryInstant(metricName);
        }

        @Test
        @DisplayName("端口返回 unavailable 降级响应时原样透传")
        void should_passThrough_when_clientReturnsUnavailable() {
            String unavailable = "{\"status\":\"unavailable\",\"metric\":\"cpu_usage\",\"reason\":\"MCP 未启用\"}";
            when(prometheusClient.queryInstant("cpu_usage")).thenReturn(unavailable);

            String result = tools.queryMetric("cpu_usage");

            assertThat(result).isEqualTo(unavailable);
        }

        @Test
        @DisplayName("指标名称大小写不敏感，CPU_USAGE 归一化为 cpu_usage 后委托")
        void should_normalizeCase_when_metricNameUpperCase() {
            when(prometheusClient.queryInstant("cpu_usage")).thenReturn("{}");

            tools.queryMetric("CPU_USAGE");

            verify(prometheusClient).queryInstant("cpu_usage");
        }

        @Test
        @DisplayName("指标名称前后空格被自动去除后委托")
        void should_trimWhitespace_when_metricNameHasSpaces() {
            when(prometheusClient.queryInstant("cpu_usage")).thenReturn("{}");

            tools.queryMetric("  cpu_usage  ");

            verify(prometheusClient).queryInstant("cpu_usage");
        }

        @Test
        @DisplayName("指标名称为 null 时抛出 IllegalArgumentException 且不查询")
        void should_throwIllegalArgument_when_metricNameIsNull() {
            assertThatThrownBy(() -> tools.queryMetric(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不能为空");
            verify(prometheusClient, never()).queryInstant(anyString());
        }

        @Test
        @DisplayName("指标名称为空白字符串时抛出 IllegalArgumentException")
        void should_throwIllegalArgument_when_metricNameIsBlank() {
            assertThatThrownBy(() -> tools.queryMetric("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不能为空");
        }

        @Test
        @DisplayName("不支持的指标名称抛出 IllegalArgumentException 并列出支持的指标")
        void should_throwIllegalArgument_when_metricNameNotSupported() {
            assertThatThrownBy(() -> tools.queryMetric("network_traffic"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不支持的指标名称")
                    .hasMessageContaining("cpu_usage")
                    .hasMessageContaining("qps");
        }
    }

    @Nested
    @DisplayName("queryMetricRange 方法")
    class QueryMetricRange {

        @Test
        @DisplayName("合法查询委托端口范围查询并透传结果")
        void should_delegateToClient_when_validQuery() {
            when(prometheusClient.queryRange("cpu_usage", 30))
                    .thenReturn("{\"metric\":\"cpu_usage\",\"samples\":[]}");

            String result = tools.queryMetricRange("cpu_usage", 30);

            assertThat(result).contains("\"metric\":\"cpu_usage\"");
            verify(prometheusClient).queryRange("cpu_usage", 30);
        }

        @Test
        @DisplayName("查询时间范围为 1 分钟时下界合法")
        void should_delegate_when_minutesIsOne() {
            when(prometheusClient.queryRange("memory_usage", 1)).thenReturn("{}");

            tools.queryMetricRange("memory_usage", 1);

            verify(prometheusClient).queryRange("memory_usage", 1);
        }

        @Test
        @DisplayName("查询时间范围为 60 分钟时上界合法")
        void should_delegate_when_minutesIsSixty() {
            when(prometheusClient.queryRange("qps", 60)).thenReturn("{}");

            tools.queryMetricRange("qps", 60);

            verify(prometheusClient).queryRange("qps", 60);
        }

        @Test
        @DisplayName("minutes 为 null 时抛出 IllegalArgumentException")
        void should_throwIllegalArgument_when_minutesIsNull() {
            assertThatThrownBy(() -> tools.queryMetricRange("cpu_usage", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("1-60");
        }

        @Test
        @DisplayName("minutes 小于 1 时抛出 IllegalArgumentException")
        void should_throwIllegalArgument_when_minutesLessThanOne() {
            assertThatThrownBy(() -> tools.queryMetricRange("cpu_usage", 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("1-60");
        }

        @Test
        @DisplayName("minutes 大于 60 时抛出 IllegalArgumentException")
        void should_throwIllegalArgument_when_minutesGreaterThanSixty() {
            assertThatThrownBy(() -> tools.queryMetricRange("cpu_usage", 61))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("1-60");
        }

        @Test
        @DisplayName("不支持的指标名称抛出 IllegalArgumentException")
        void should_throwIllegalArgument_when_metricNameNotSupported() {
            assertThatThrownBy(() -> tools.queryMetricRange("unknown_metric", 10))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不支持的指标名称");
        }
    }
}
