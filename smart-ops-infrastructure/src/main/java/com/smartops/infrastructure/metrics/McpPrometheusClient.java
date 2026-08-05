package com.smartops.infrastructure.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 基于 MCP 的 {@link PrometheusClient} 实现。
 *
 * <p>从 {@link SyncMcpToolCallbackProvider} 解析 Prometheus MCP Server 暴露的
 * 查询工具，将标准化指标名翻译为 PromQL 后委托调用。
 * 仅在 {@code smartops.mcp.enabled=true} 时注册。</p>
 *
 * <p><b>工具名匹配</b>：按候选名子串匹配（忽略大小写），兼容带连接名前缀的
 * 工具注册名（如 {@code prometheus_execute_query}）。
 * 瞬时查询候选：execute_query / instant_query；
 * 范围查询候选：execute_range_query / query_range。
 * 匹配不到、调用失败或返回空时，返回显式 unavailable JSON，绝不编造数据。</p>
 *
 * <p><b>PromQL 映射</b>：基于 node_exporter / 标准 HTTP 指标的常用表达式，
 * 实际部署的指标 schema 不同时需调整本映射（属环境适配，非代码缺陷）。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(prefix = "smartops.mcp", name = "enabled", havingValue = "true")
public class McpPrometheusClient implements PrometheusClient {

    private static final Logger log = LoggerFactory.getLogger(McpPrometheusClient.class);

    /** 瞬时查询工具候选名（子串匹配，忽略大小写）。 */
    private static final List<String> INSTANT_TOOL_CANDIDATES = List.of("execute_query", "instant_query");

    /** 范围查询工具候选名（子串匹配，忽略大小写）。 */
    private static final List<String> RANGE_TOOL_CANDIDATES = List.of("execute_range_query", "query_range");

    /** 标准化指标名 → PromQL 表达式映射。 */
    private static final Map<String, String> PROMQL = Map.of(
            "cpu_usage", "100 - (avg(rate(node_cpu_seconds_total{mode=\"idle\"}[5m])) * 100)",
            "memory_usage", "(1 - (node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes)) * 100",
            "disk_usage", "100 - ((node_filesystem_avail_bytes{mountpoint=\"/\"} "
                    + "/ node_filesystem_size_bytes{mountpoint=\"/\"}) * 100)",
            "qps", "sum(rate(http_requests_total[5m]))"
    );

    private final ObjectProvider<SyncMcpToolCallbackProvider> toolCallbackProvider;

    /**
     * 构造 MCP Prometheus 客户端。
     *
     * @param toolCallbackProvider MCP 工具回调提供者（自动配置创建，可能尚未就绪）
     */
    public McpPrometheusClient(ObjectProvider<SyncMcpToolCallbackProvider> toolCallbackProvider) {
        this.toolCallbackProvider = toolCallbackProvider;
    }

    @Override
    public String queryInstant(String metricName) {
        String args = "{\"query\":\"" + escapeJson(promqlOf(metricName)) + "\"}";
        return execute(INSTANT_TOOL_CANDIDATES, args, metricName);
    }

    @Override
    public String queryRange(String metricName, int minutes) {
        long end = Instant.now().getEpochSecond();
        long start = end - minutes * 60L;
        String args = String.format(
                "{\"query\":\"%s\",\"start\":%d,\"end\":%d,\"step\":%d}",
                escapeJson(promqlOf(metricName)), start, end, Math.max(1, minutes));
        return execute(RANGE_TOOL_CANDIDATES, args, metricName);
    }

    /**
     * 解析工具并调用，失败时降级为 unavailable JSON。
     *
     * @param candidates 工具候选名列表
     * @param args       工具入参 JSON
     * @param metricName 指标名（用于降级响应）
     * @return MCP 工具原始返回，或显式 unavailable JSON
     */
    private String execute(List<String> candidates, String args, String metricName) {
        ToolCallback callback = findTool(candidates);
        if (callback == null) {
            log.warn("MCP Server 未提供 Prometheus 查询工具，候选: {}", candidates);
            return unavailable(metricName, "MCP Server 未提供 Prometheus 查询工具");
        }
        try {
            String result = callback.call(args);
            if (result == null || result.isBlank()) {
                return unavailable(metricName, "MCP 工具返回空结果");
            }
            return result;
        } catch (Exception e) {
            log.warn("MCP 工具调用失败: metric={}, error={}", metricName, e.getMessage());
            return unavailable(metricName, "MCP 工具调用失败: " + sanitize(e.getMessage()));
        }
    }

    /**
     * 按候选名子串匹配（忽略大小写）解析 MCP 工具。
     *
     * @param candidates 候选名列表
     * @return 匹配到的工具回调；未匹配或 Provider 未就绪时返回 null
     */
    private ToolCallback findTool(List<String> candidates) {
        SyncMcpToolCallbackProvider provider = toolCallbackProvider.getIfAvailable();
        if (provider == null) {
            return null;
        }
        for (ToolCallback callback : provider.getToolCallbacks()) {
            String toolName = callback.getToolDefinition().name().toLowerCase(Locale.ROOT);
            for (String candidate : candidates) {
                if (toolName.contains(candidate)) {
                    return callback;
                }
            }
        }
        return null;
    }

    /**
     * 获取指标对应的 PromQL 表达式；无映射时按原始指标名作为 PromQL。
     *
     * @param metricName 标准化指标名（调用方已校验合法）
     * @return PromQL 表达式
     */
    private String promqlOf(String metricName) {
        return PROMQL.getOrDefault(metricName, metricName);
    }

    /**
     * 构造显式不可用响应。
     *
     * @param metricName 指标名
     * @param reason     不可用原因（已消毒，不含双引号）
     * @return unavailable JSON
     */
    private String unavailable(String metricName, String reason) {
        return String.format("{\"status\":\"unavailable\",\"metric\":\"%s\",\"reason\":\"%s\"}",
                metricName, reason);
    }

    /**
     * 消毒异常消息：替换双引号，防止破坏 JSON 结构。
     *
     * @param message 原始异常消息，可为 null
     * @return 消毒后的消息
     */
    private String sanitize(String message) {
        return message == null ? "未知错误" : message.replace('"', '\'');
    }

    /**
     * 转义 JSON 字符串值中的双引号与反斜杠。
     *
     * @param value 原始值
     * @return 转义后的值
     */
    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
