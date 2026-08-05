package com.smartops.infrastructure.metrics;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * MCP 未启用时的 {@link PrometheusClient} 降级实现。
 *
 * <p>当 {@code smartops.mcp.enabled=false}（默认）时注册，
 * 所有查询返回显式 unavailable JSON，让 LLM 如实告知用户
 * "监控数据通道未接入"，而不是编造随机指标（修复 P0-2 缺陷）。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(prefix = "smartops.mcp", name = "enabled", havingValue = "false", matchIfMissing = true)
public class UnavailablePrometheusClient implements PrometheusClient {

    /** 降级原因：MCP 通道未启用。 */
    private static final String REASON = "Prometheus MCP 通道未启用（smartops.mcp.enabled=false）";

    @Override
    public String queryInstant(String metricName) {
        return unavailable(metricName);
    }

    @Override
    public String queryRange(String metricName, int minutes) {
        return unavailable(metricName);
    }

    /**
     * 构造显式不可用响应。
     *
     * @param metricName 指标名
     * @return unavailable JSON
     */
    private String unavailable(String metricName) {
        return String.format("{\"status\":\"unavailable\",\"metric\":\"%s\",\"reason\":\"%s\"}",
                metricName, REASON);
    }
}
