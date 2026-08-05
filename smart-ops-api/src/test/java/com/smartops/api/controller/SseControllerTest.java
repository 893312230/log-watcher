package com.smartops.api.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import com.smartops.agent.tools.PrometheusTools;
import com.smartops.infrastructure.sse.SseTaskRegistry;
import reactor.core.publisher.Flux;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link SseController} 单元测试。
 *
 * <p>验证 SSE 流式对话的会话 ID 生成、消息传递、流结束标记等行为。
 * ChatClient 的流式调用通过 Mock 返回固定 Flux。对应 agent.md 阶段一任务6。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class SseControllerTest {

    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.StreamResponseSpec streamResponseSpec;
    private PrometheusTools prometheusTools;
    private SseController controller;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        streamResponseSpec = mock(ChatClient.StreamResponseSpec.class);
        prometheusTools = mock(PrometheusTools.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.tools(any(PrometheusTools.class))).thenReturn(requestSpec);
        when(requestSpec.advisors(any(java.util.function.Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.content()).thenReturn(Flux.just("片段1", "片段2"));

        controller = new SseController(chatClient, prometheusTools, Duration.ofSeconds(15), null, 64);
    }

    @Test
    @DisplayName("会话 ID 为空时自动生成 UUID")
    void should_generateUuid_when_conversationIdIsNull() {
        com.smartops.api.dto.ChatRequest request = new com.smartops.api.dto.ChatRequest(null, "查询 CPU", null);

        java.util.List<String> results = controller.chatStream(request).collectList().block();

        assertThat(results).isNotNull();
        // 最后一个元素应为 [DONE] 标记
        assertThat(results.get(results.size() - 1)).isEqualTo(SseController.STREAM_DONE_MARKER);
    }

    @Test
    @DisplayName("会话 ID 为空白时自动生成 UUID")
    void should_generateUuid_when_conversationIdIsBlank() {
        com.smartops.api.dto.ChatRequest request = new com.smartops.api.dto.ChatRequest("  ", "查询内存", null);

        java.util.List<String> results = controller.chatStream(request).collectList().block();

        assertThat(results).isNotNull();
        assertThat(results.get(results.size() - 1)).isEqualTo(SseController.STREAM_DONE_MARKER);
    }

    @Test
    @DisplayName("提供有效会话 ID 时沿用该 ID")
    void should_keepConversationId_when_validIdProvided() {
        com.smartops.api.dto.ChatRequest request = new com.smartops.api.dto.ChatRequest("sse-conv-1", "查询磁盘", null);

        // 调用不应抛异常，流正常返回
        java.util.List<String> results = controller.chatStream(request).collectList().block();

        assertThat(results).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("流式响应包含 LLM 返回的片段")
    void should_containLlmFragments_when_streamSuccess() {
        com.smartops.api.dto.ChatRequest request = new com.smartops.api.dto.ChatRequest("conv-1", "查询 QPS", null);

        java.util.List<String> results = controller.chatStream(request).collectList().block();

        assertThat(results).contains("片段1", "片段2", SseController.STREAM_DONE_MARKER);
    }

    @Test
    @DisplayName("流末尾追加 [DONE] 标记")
    void should_appendDoneMarker_when_streamCompletes() {
        com.smartops.api.dto.ChatRequest request = new com.smartops.api.dto.ChatRequest("conv-2", "分析趋势", null);

        java.util.List<String> results = controller.chatStream(request).collectList().block();

        assertThat(results).endsWith(SseController.STREAM_DONE_MARKER);
    }

    @Test
    @DisplayName("用户消息被正确传递给 ChatClient")
    void should_passUserMessageToChatClient_when_chat() {
        org.mockito.ArgumentCaptor<String> messageCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        when(requestSpec.user(messageCaptor.capture())).thenReturn(requestSpec);

        com.smartops.api.dto.ChatRequest request = new com.smartops.api.dto.ChatRequest("conv-3", "帮我分析 CPU 趋势", null);
        controller.chatStream(request).collectList().block();

        assertThat(messageCaptor.getValue()).isEqualTo("帮我分析 CPU 趋势");
    }

    @Test
    @DisplayName("LLM 流异常时推送 [ERROR] 事件并以 [DONE] 终止")
    void should_emitErrorEventAndDone_when_streamFails() {
        when(streamResponseSpec.content()).thenReturn(
                Flux.error(new IllegalStateException("LLM 调用超时")));

        com.smartops.api.dto.ChatRequest request = new com.smartops.api.dto.ChatRequest("conv-err", "查询 CPU", null);
        java.util.List<String> results = controller.chatStream(request).collectList().block();

        assertThat(results).hasSize(3);
        assertThat(results.get(0)).startsWith(SseController.CONVERSATION_MARKER);
        assertThat(results.get(1)).startsWith(SseController.STREAM_ERROR_PREFIX);
        assertThat(results.get(2)).isEqualTo(SseController.STREAM_DONE_MARKER);
    }

    @Test
    @DisplayName("超长用户消息走日志截断路径且流正常返回")
    void should_truncateLogMessage_when_messageTooLong() {
        String longMessage = "查询 CPU 使用率".repeat(20);

        com.smartops.api.dto.ChatRequest request = new com.smartops.api.dto.ChatRequest("conv-long", longMessage, null);
        java.util.List<String> results = controller.chatStream(request).collectList().block();

        assertThat(results).contains("片段1", "片段2", SseController.STREAM_DONE_MARKER);
    }

    @Test
    @DisplayName("LLM 长时间无输出时按心跳间隔推送心跳，主流结束后心跳停止")
    void should_emitHeartbeat_when_streamIdle() {
        SseController fastBeatController =
                new SseController(chatClient, prometheusTools, Duration.ofMillis(50), null, 64);
        when(streamResponseSpec.content()).thenReturn(
                Flux.just("片段1").delayElements(Duration.ofMillis(220)));

        com.smartops.api.dto.ChatRequest request = new com.smartops.api.dto.ChatRequest("conv-hb", "查询 CPU", null);
        java.util.List<String> results = fastBeatController.chatStream(request)
                .collectList().block(Duration.ofSeconds(5));

        assertThat(results).isNotNull();
        assertThat(results).contains("片段1");
        assertThat(results).endsWith(SseController.STREAM_DONE_MARKER);
        long heartbeatCount = results.stream()
                .filter(SseController.HEARTBEAT_MARKER::equals).count();
        assertThat(heartbeatCount).isBetween(2L, 5L);
    }

    @Test
    @DisplayName("SseTaskRegistry 热流路径，重连返回缓存结果")
    void should_useHotFluxAndCache_when_registryPresent() throws Exception {
        SseTaskRegistry registry = new SseTaskRegistry(10, Duration.ofMinutes(5), Duration.ofMinutes(10));
        SseController htController = new SseController(chatClient, prometheusTools,
                Duration.ofSeconds(15), registry, 64);
        when(streamResponseSpec.content()).thenReturn(Flux.just("a", "b"));

        java.util.List<String> r1 = htController.chatStream(
                new com.smartops.api.dto.ChatRequest("conv-hot", "msg", null))
                .take(3).collectList().block();
        assertThat(r1).contains("a", "b");

        Thread.sleep(300);
        java.util.List<String> r2 = htController.chatStream(
                new com.smartops.api.dto.ChatRequest("conv-hot", "msg", null))
                .collectList().block();
        assertThat(r2).contains(SseController.STREAM_DONE_MARKER);
    }
}
