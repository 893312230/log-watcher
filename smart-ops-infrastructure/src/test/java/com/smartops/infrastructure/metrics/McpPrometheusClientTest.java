package com.smartops.infrastructure.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link McpPrometheusClient} 单元测试。
 *
 * <p>Mock {@link SyncMcpToolCallbackProvider} 与 {@link ToolCallback}，
 * 验证工具名匹配、PromQL 入参构造、以及所有降级路径
 * （Provider 未就绪 / 工具缺失 / 调用异常 / 空结果）均返回显式
 * unavailable JSON 而非编造数据。不连接真实 MCP Server。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class McpPrometheusClientTest {

    private ObjectProvider<SyncMcpToolCallbackProvider> providerObjectProvider;
    private SyncMcpToolCallbackProvider toolCallbackProvider;
    private McpPrometheusClient client;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        providerObjectProvider = mock(ObjectProvider.class);
        toolCallbackProvider = mock(SyncMcpToolCallbackProvider.class);
        when(providerObjectProvider.getIfAvailable()).thenReturn(toolCallbackProvider);
        client = new McpPrometheusClient(providerObjectProvider);
    }

    /**
     * 构造指定名称的 Mock 工具回调。
     *
     * @param toolName 工具注册名
     * @return Mock 工具回调
     */
    private ToolCallback mockTool(String toolName) {
        ToolCallback callback = mock(ToolCallback.class);
        ToolDefinition definition = mock(ToolDefinition.class);
        when(definition.name()).thenReturn(toolName);
        when(callback.getToolDefinition()).thenReturn(definition);
        return callback;
    }

    @Test
    @DisplayName("瞬时查询匹配 execute_query 工具并传入 PromQL")
    void should_callInstantTool_when_toolAvailable() {
        ToolCallback queryTool = mockTool("execute_query");
        when(toolCallbackProvider.getToolCallbacks()).thenReturn(new ToolCallback[]{queryTool});
        when(queryTool.call(anyString())).thenReturn("{\"values\":[[1753100000,\"42.0\"]]}");

        String result = client.queryInstant("cpu_usage");

        assertThat(result).isEqualTo("{\"values\":[[1753100000,\"42.0\"]]}");
        org.mockito.ArgumentCaptor<String> argsCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(queryTool).call(argsCaptor.capture());
        // PromQL 中的双引号必须被转义，且包含 node_cpu 表达式
        assertThat(argsCaptor.getValue()).startsWith("{\"query\":\"");
        assertThat(argsCaptor.getValue()).contains("node_cpu_seconds_total");
        assertThat(argsCaptor.getValue()).contains("mode=\\\"idle\\\"");
    }

    @Test
    @DisplayName("范围查询匹配带连接名前缀的工具并传入起止时间与步长")
    void should_callRangeTool_when_prefixedToolName() {
        ToolCallback rangeTool = mockTool("prometheus_execute_range_query");
        when(toolCallbackProvider.getToolCallbacks()).thenReturn(new ToolCallback[]{rangeTool});
        when(rangeTool.call(anyString())).thenReturn("{\"values\":[]}");

        String result = client.queryRange("qps", 30);

        assertThat(result).isEqualTo("{\"values\":[]}");
        org.mockito.ArgumentCaptor<String> argsCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(rangeTool).call(argsCaptor.capture());
        assertThat(argsCaptor.getValue()).contains("http_requests_total");
        assertThat(argsCaptor.getValue()).contains("\"start\":");
        assertThat(argsCaptor.getValue()).contains("\"end\":");
        assertThat(argsCaptor.getValue()).contains("\"step\":30");
    }

    @Test
    @DisplayName("工具名大小写不敏感地匹配候选名")
    void should_matchCaseInsensitively_when_toolNameUpperCase() {
        ToolCallback queryTool = mockTool("EXECUTE_QUERY");
        when(toolCallbackProvider.getToolCallbacks()).thenReturn(new ToolCallback[]{queryTool});
        when(queryTool.call(anyString())).thenReturn("ok");

        String result = client.queryInstant("memory_usage");

        assertThat(result).isEqualTo("ok");
    }

    @Test
    @DisplayName("Provider 未就绪时返回显式 unavailable")
    void should_returnUnavailable_when_providerNotReady() {
        when(providerObjectProvider.getIfAvailable()).thenReturn(null);

        String result = client.queryInstant("cpu_usage");

        assertThat(result).contains("\"status\":\"unavailable\"");
        assertThat(result).contains("未提供 Prometheus 查询工具");
    }

    @Test
    @DisplayName("MCP Server 无匹配工具时返回显式 unavailable")
    void should_returnUnavailable_when_noMatchingTool() {
        ToolCallback unrelatedTool = mockTool("list_alerts");
        when(toolCallbackProvider.getToolCallbacks()).thenReturn(new ToolCallback[]{unrelatedTool});

        String result = client.queryInstant("cpu_usage");

        assertThat(result).contains("\"status\":\"unavailable\"");
        assertThat(result).contains("未提供 Prometheus 查询工具");
    }

    @Test
    @DisplayName("工具调用抛异常时返回显式 unavailable 且原因不含双引号")
    void should_returnUnavailable_when_toolCallThrows() {
        ToolCallback queryTool = mockTool("execute_query");
        when(toolCallbackProvider.getToolCallbacks()).thenReturn(new ToolCallback[]{queryTool});
        when(queryTool.call(anyString())).thenThrow(new RuntimeException("连接 \"prometheus\" 超时"));

        String result = client.queryInstant("cpu_usage");

        assertThat(result).contains("\"status\":\"unavailable\"");
        assertThat(result).contains("MCP 工具调用失败");
        // reason 字段内的双引号已被替换为单引号，JSON 结构完整
        assertThat(result).contains("连接 'prometheus' 超时");
        assertThat(result).endsWith("\"}");
    }

    @Test
    @DisplayName("工具调用抛出无消息异常时原因为未知错误")
    void should_returnUnknownReason_when_exceptionMessageNull() {
        ToolCallback queryTool = mockTool("execute_query");
        when(toolCallbackProvider.getToolCallbacks()).thenReturn(new ToolCallback[]{queryTool});
        when(queryTool.call(anyString())).thenThrow(new RuntimeException((String) null));

        String result = client.queryInstant("cpu_usage");

        assertThat(result).contains("未知错误");
    }

    @Test
    @DisplayName("工具返回空白结果时返回显式 unavailable")
    void should_returnUnavailable_when_toolReturnsBlank() {
        ToolCallback queryTool = mockTool("execute_query");
        when(toolCallbackProvider.getToolCallbacks()).thenReturn(new ToolCallback[]{queryTool});
        when(queryTool.call(anyString())).thenReturn("  ");

        String result = client.queryInstant("cpu_usage");

        assertThat(result).contains("\"status\":\"unavailable\"");
        assertThat(result).contains("空结果");
    }

    @Test
    @DisplayName("无 PromQL 映射的指标按原始名称作为查询表达式")
    void should_useRawMetricName_when_noPromqlMapping() {
        ToolCallback queryTool = mockTool("execute_query");
        when(toolCallbackProvider.getToolCallbacks()).thenReturn(new ToolCallback[]{queryTool});
        when(queryTool.call(anyString())).thenReturn("ok");

        client.queryInstant("custom_metric");

        org.mockito.ArgumentCaptor<String> argsCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(queryTool).call(argsCaptor.capture());
        assertThat(argsCaptor.getValue()).isEqualTo("{\"query\":\"custom_metric\"}");
    }
}
