package com.smartops.infrastructure.observability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ObservedToolCallingManager} 单元测试。
 *
 * <p>覆盖：成功路径逐工具记录成功观测、失败路径记录失败观测并重抛、
 * 无工具调用不记录、多 Generation 聚合、入参超长截断、
 * observability 为 null 时纯透传、resolveToolDefinitions 透传。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class ObservedToolCallingManagerTest {

    private ToolCallingManager delegate;
    private Observability observability;
    private ObservedToolCallingManager manager;
    private Prompt prompt;
    private ToolExecutionResult executionResult;

    @BeforeEach
    void setUp() {
        delegate = mock(ToolCallingManager.class);
        observability = mock(Observability.class);
        manager = new ObservedToolCallingManager(delegate, observability);
        prompt = mock(Prompt.class);
        executionResult = mock(ToolExecutionResult.class);
    }

    private ChatResponse responseWithToolCalls(AssistantMessage.ToolCall... toolCalls) {
        AssistantMessage assistantMessage = mock(AssistantMessage.class);
        when(assistantMessage.hasToolCalls()).thenReturn(toolCalls.length > 0);
        when(assistantMessage.getToolCalls()).thenReturn(List.of(toolCalls));
        Generation generation = mock(Generation.class);
        when(generation.getOutput()).thenReturn(assistantMessage);
        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.getResults()).thenReturn(List.of(generation));
        return chatResponse;
    }

    @Test
    @DisplayName("成功路径逐工具记录成功观测并透传结果")
    void should_recordSuccessPerTool_when_executionSucceeds() {
        AssistantMessage.ToolCall call1 = new AssistantMessage.ToolCall("id-1", "function", "queryMetric", "{\"a\":1}");
        AssistantMessage.ToolCall call2 = new AssistantMessage.ToolCall("id-2", "function", "restartService", "{}");
        ChatResponse chatResponse = responseWithToolCalls(call1, call2);
        when(delegate.executeToolCalls(prompt, chatResponse)).thenReturn(executionResult);

        ToolExecutionResult result = manager.executeToolCalls(prompt, chatResponse);

        assertThat(result).isSameAs(executionResult);
        verify(observability).recordToolCall(eq("queryMetric"), eq(true), anyLong(), eq("{\"a\":1}"));
        verify(observability).recordToolCall(eq("restartService"), eq(true), anyLong(), eq("{}"));
    }

    @Test
    @DisplayName("失败路径记录失败观测并重抛异常")
    void should_recordFailureAndRethrow_when_executionFails() {
        AssistantMessage.ToolCall call = new AssistantMessage.ToolCall("id-1", "function", "queryMetric", "{}");
        ChatResponse chatResponse = responseWithToolCalls(call);
        when(delegate.executeToolCalls(prompt, chatResponse))
                .thenThrow(new IllegalStateException("tool down"));

        assertThatThrownBy(() -> manager.executeToolCalls(prompt, chatResponse))
                .isInstanceOf(IllegalStateException.class);

        verify(observability).recordToolCall(eq("queryMetric"), eq(false), anyLong(),
                contains("tool down"));
    }

    @Test
    @DisplayName("无工具调用时不记录观测")
    void should_notRecord_when_noToolCalls() {
        ChatResponse chatResponse = responseWithToolCalls();
        when(delegate.executeToolCalls(prompt, chatResponse)).thenReturn(executionResult);

        manager.executeToolCalls(prompt, chatResponse);

        verify(observability, never()).recordToolCall(anyString(), any(Boolean.class), anyLong(), any());
    }

    @Test
    @DisplayName("ChatResponse 为 null 或 results 为 null 时不记录观测")
    void should_notRecord_when_responseOrResultsNull() {
        ChatResponse nullResults = mock(ChatResponse.class);
        when(nullResults.getResults()).thenReturn(null);
        when(delegate.executeToolCalls(prompt, null)).thenReturn(executionResult);
        when(delegate.executeToolCalls(prompt, nullResults)).thenReturn(executionResult);

        manager.executeToolCalls(prompt, null);
        manager.executeToolCalls(prompt, nullResults);

        verify(observability, never()).recordToolCall(anyString(), any(Boolean.class), anyLong(), any());
    }

    @Test
    @DisplayName("入参超长时截断记录")
    void should_truncateArguments_when_tooLong() {
        String longArgs = "x".repeat(ObservedToolCallingManager.ARGUMENTS_MAX_LENGTH + 100);
        AssistantMessage.ToolCall call = new AssistantMessage.ToolCall("id-1", "function", "queryMetric", longArgs);
        ChatResponse chatResponse = responseWithToolCalls(call);
        when(delegate.executeToolCalls(prompt, chatResponse)).thenReturn(executionResult);

        manager.executeToolCalls(prompt, chatResponse);

        verify(observability).recordToolCall(eq("queryMetric"), eq(true), anyLong(),
                eq("x".repeat(ObservedToolCallingManager.ARGUMENTS_MAX_LENGTH) + "..."));
    }

    @Test
    @DisplayName("Generation 输出为 null 被过滤，null 入参记录为空串")
    void should_filterNullOutputAndRecordEmpty_when_nullOutputOrArguments() {
        AssistantMessage.ToolCall call = new AssistantMessage.ToolCall("id-1", "function", "queryMetric", null);
        AssistantMessage assistantMessage = mock(AssistantMessage.class);
        when(assistantMessage.hasToolCalls()).thenReturn(true);
        when(assistantMessage.getToolCalls()).thenReturn(List.of(call));
        Generation withOutput = mock(Generation.class);
        when(withOutput.getOutput()).thenReturn(assistantMessage);
        Generation nullOutput = mock(Generation.class);
        when(nullOutput.getOutput()).thenReturn(null);
        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.getResults()).thenReturn(List.of(nullOutput, withOutput));
        when(delegate.executeToolCalls(prompt, chatResponse)).thenReturn(executionResult);

        manager.executeToolCalls(prompt, chatResponse);

        verify(observability).recordToolCall(eq("queryMetric"), eq(true), anyLong(), eq(""));
    }

    @Test
    @DisplayName("observability 为 null 时纯透传不抛异常")
    void should_passthrough_when_observabilityNull() {
        ObservedToolCallingManager plain = new ObservedToolCallingManager(delegate, null);
        AssistantMessage.ToolCall call = new AssistantMessage.ToolCall("id-1", "function", "queryMetric", "{}");
        ChatResponse chatResponse = responseWithToolCalls(call);
        when(delegate.executeToolCalls(prompt, chatResponse)).thenReturn(executionResult);

        ToolExecutionResult result = plain.executeToolCalls(prompt, chatResponse);

        assertThat(result).isSameAs(executionResult);
    }

    @Test
    @DisplayName("resolveToolDefinitions 直接透传委托")
    void should_delegateResolveToolDefinitions() {
        manager.resolveToolDefinitions(null);

        verify(delegate).resolveToolDefinitions(null);
    }

    @Test
    @DisplayName("semaphore 存在时限制并发调用")
    void should_acquireSemaphore_when_configured() {
        ToolCallingManager delegate = mock(ToolCallingManager.class);
        ToolExecutionResult result = mock(ToolExecutionResult.class);
        when(delegate.executeToolCalls(any(), any())).thenReturn(result);
        ChatResponse chatResponse = responseWithToolCalls(
                new AssistantMessage.ToolCall("id", "function", "tool", "{}"));
        ObservedToolCallingManager mgr = new ObservedToolCallingManager(
                delegate, null, 2, 5000);

        mgr.executeToolCalls(mock(Prompt.class), chatResponse);
        mgr.executeToolCalls(mock(Prompt.class), chatResponse);

        verify(delegate, org.mockito.Mockito.times(2))
                .executeToolCalls(any(), any());
    }

    @Test
    @DisplayName("semaphore 为 0 时不限并发（semaphore=null 路径）")
    void should_notLimit_when_maxConcurrentZero() {
        ToolCallingManager delegate = mock(ToolCallingManager.class);
        when(delegate.executeToolCalls(any(), any())).thenReturn(mock(ToolExecutionResult.class));
        ChatResponse chatResponse = responseWithToolCalls(
                new AssistantMessage.ToolCall("id", "function", "tool", "{}"));
        ObservedToolCallingManager mgr = new ObservedToolCallingManager(
                delegate, null, 0, 0);

        mgr.executeToolCalls(mock(Prompt.class), chatResponse);
        verify(delegate).executeToolCalls(any(), any());
    }
}
