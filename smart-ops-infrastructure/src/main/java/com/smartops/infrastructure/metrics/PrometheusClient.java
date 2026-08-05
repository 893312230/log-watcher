package com.smartops.infrastructure.metrics;

/**
 * Prometheus 指标查询端口。
 *
 * <p>隔离 agent-core 的 {@code PrometheusTools} 与具体查询通道（MCP Client），
 * 使工具层不依赖 MCP 实现细节，且单元测试可 Mock 本端口。
 * 实现选择由 {@code smartops.mcp.enabled} 控制：
 * true → {@link McpPrometheusClient}（委托 MCP Server 工具）；
 * false → {@link UnavailablePrometheusClient}（返回显式不可用响应）。</p>
 *
 * <p>实现约定：返回值必须是 JSON 字符串。查询失败/不可用时不抛异常，
 * 返回 {@code {"status":"unavailable","reason":"..."}} 结构，
 * 由 LLM 向用户解释降级原因（杜绝编造监控数据）。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public interface PrometheusClient {

    /**
     * 查询指定指标的当前瞬时值。
     *
     * @param metricName 标准化后的指标名（小写），如 cpu_usage
     * @return 查询结果 JSON；不可用时返回显式 unavailable 结构
     */
    String queryInstant(String metricName);

    /**
     * 查询指定指标最近一段时间的范围数据。
     *
     * @param metricName 标准化后的指标名（小写），如 cpu_usage
     * @param minutes    查询最近多少分钟，已由调用方校验在 1-60 之间
     * @return 查询结果 JSON；不可用时返回显式 unavailable 结构
     */
    String queryRange(String metricName, int minutes);
}
