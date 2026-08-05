package com.smartops.infrastructure.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link UnavailablePrometheusClient} 单元测试。
 *
 * <p>验证 MCP 未启用时的降级实现：所有查询返回显式 unavailable JSON，
 * 携带明确的不可用原因，绝不编造监控数据（P0-2 缺陷的兜底防线）。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class UnavailablePrometheusClientTest {

    private final UnavailablePrometheusClient client = new UnavailablePrometheusClient();

    @Test
    @DisplayName("瞬时查询返回显式 unavailable 及原因")
    void should_returnUnavailable_when_queryInstant() {
        String result = client.queryInstant("cpu_usage");

        assertThat(result).contains("\"status\":\"unavailable\"");
        assertThat(result).contains("\"metric\":\"cpu_usage\"");
        assertThat(result).contains("smartops.mcp.enabled=false");
    }

    @Test
    @DisplayName("范围查询返回显式 unavailable 及原因")
    void should_returnUnavailable_when_queryRange() {
        String result = client.queryRange("memory_usage", 30);

        assertThat(result).contains("\"status\":\"unavailable\"");
        assertThat(result).contains("\"metric\":\"memory_usage\"");
        assertThat(result).contains("Prometheus MCP 通道未启用");
    }
}
