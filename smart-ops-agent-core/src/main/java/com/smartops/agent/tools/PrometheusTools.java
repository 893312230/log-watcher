package com.smartops.agent.tools;

import com.smartops.infrastructure.metrics.PrometheusClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Prometheus 运维指标查询工具。
 *
 * <p>对应 agent.md 阶段一任务4（实现基础 Tool Calling）。
 * 通过 {@code @Tool} 注解声明，ChatClient 可自动调用。</p>
 *
 * <p><b>数据来源</b>：查询经 {@link PrometheusClient} 端口委托给
 * Prometheus MCP Server（smartops.mcp.enabled=true 时）；
 * MCP 未启用或查询失败时返回显式
 * {@code {"status":"unavailable","reason":"..."}} 降级响应，
 * 由 LLM 如实告知用户，绝不编造监控数据。</p>
 *
 * <p>支持的指标：
 * <ul>
 *   <li>{@code cpu_usage} - CPU 使用率（百分比）</li>
 *   <li>{@code memory_usage} - 内存使用率（百分比）</li>
 *   <li>{@code disk_usage} - 磁盘使用率（百分比）</li>
 *   <li>{@code qps} - 每秒查询数</li>
 * </ul></p>
 *
 * <p>线程安全：无内部状态，依赖的 PrometheusClient 实现均为无状态 Bean。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Component
public class PrometheusTools {

    /**
     * 支持的指标名称与中文描述映射，用于参数校验与错误提示。
     */
    private static final Map<String, String> SUPPORTED_METRICS = Map.of(
            "cpu_usage", "CPU 使用率",
            "memory_usage", "内存使用率",
            "disk_usage", "磁盘使用率",
            "qps", "每秒查询数"
    );

    private final PrometheusClient prometheusClient;

    /**
     * 构造 Prometheus 工具。
     *
     * @param prometheusClient 指标查询端口（MCP 实现或不可用降级实现，由配置决定）
     */
    public PrometheusTools(PrometheusClient prometheusClient) {
        this.prometheusClient = prometheusClient;
    }

    /**
     * 查询指定指标的当前值。
     *
     * <p>模型可根据用户自然语言问题（如"查询 CPU 使用率"）自动选择调用此工具。
     * 结果来自真实 Prometheus 查询；通道不可用时返回显式 unavailable 响应。</p>
     *
     * @param metricName 指标名称，支持 cpu_usage / memory_usage / disk_usage / qps
     * @return 指标查询结果 JSON，或显式 unavailable JSON
     * @throws IllegalArgumentException 当指标名称不被支持时
     */
    @Tool(description = "查询 Prometheus 运维指标的当前值。支持的指标：cpu_usage(CPU使用率)、memory_usage(内存使用率)、disk_usage(磁盘使用率)、qps(每秒查询数)。当用户询问系统资源使用情况或性能指标时调用此工具。")
    public String queryMetric(
            @ToolParam(description = "指标名称，必须是以下之一：cpu_usage / memory_usage / disk_usage / qps")
            String metricName
    ) {
        return prometheusClient.queryInstant(normalizeAndValidate(metricName));
    }

    /**
     * 查询指定指标在最近一段时间内的趋势。
     *
     * <p>返回一段时间范围内的采样数据，便于模型分析趋势。
     * 结果来自真实 Prometheus 范围查询；通道不可用时返回显式 unavailable 响应。</p>
     *
     * @param metricName 指标名称，支持 cpu_usage / memory_usage / disk_usage / qps
     * @param minutes    查询最近多少分钟的数据，范围 1-60
     * @return 趋势查询结果 JSON，或显式 unavailable JSON
     * @throws IllegalArgumentException 当指标名称不支持或 minutes 超出范围时
     */
    @Tool(description = "查询 Prometheus 运维指标在最近一段时间内的趋势数据。返回多个时间点的采样值，用于分析指标变化趋势。当用户询问指标历史趋势、变化情况或需要对比分析时调用此工具。")
    public String queryMetricRange(
            @ToolParam(description = "指标名称，必须是以下之一：cpu_usage / memory_usage / disk_usage / qps")
            String metricName,
            @ToolParam(description = "查询最近多少分钟的数据，范围 1-60")
            Integer minutes
    ) {
        if (minutes == null || minutes < 1 || minutes > 60) {
            throw new IllegalArgumentException("查询时间范围必须在 1-60 分钟之间");
        }
        return prometheusClient.queryRange(normalizeAndValidate(metricName), minutes);
    }

    /**
     * 标准化并校验指标名称：去除首尾空白、转小写、校验是否在支持列表中。
     *
     * @param metricName 原始指标名称
     * @return 标准化后的指标名称
     * @throws IllegalArgumentException 当名称为空或不支持时
     */
    private String normalizeAndValidate(String metricName) {
        if (metricName == null || metricName.isBlank()) {
            throw new IllegalArgumentException("指标名称不能为空");
        }
        String normalized = metricName.trim().toLowerCase();
        if (!SUPPORTED_METRICS.containsKey(normalized)) {
            throw new IllegalArgumentException(
                    "不支持的指标名称: " + metricName + "，支持的指标: " + SUPPORTED_METRICS.keySet()
            );
        }
        return normalized;
    }
}
