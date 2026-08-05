package com.smartops.infrastructure.chat;

import com.smartops.common.exception.LlmCallException;
import com.smartops.infrastructure.observability.Observability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ChatService} 可观测性钩子测试。
 *
 * <p>验证 invokeLlm 汇聚点在成功/失败路径均记录指标与审计，
 * observability 为 null（旧构造器）时不影响调用。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class ChatServiceObservabilityTest {

    private ChatClient memoryChatClient;
    private ChatClient statelessChatClient;
    private ChatClient.ChatClientRequestSpec statelessSpec;
    private ChatClient.CallResponseSpec callResponseSpec;
    private Observability observability;
    private ChatService service;

    @BeforeEach
    void setUp() {
        memoryChatClient = mock(ChatClient.class);
        statelessChatClient = mock(ChatClient.class);
        ChatClient.Builder chatClientBuilder = mock(ChatClient.Builder.class);
        statelessSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callResponseSpec = mock(ChatClient.CallResponseSpec.class);
        observability = mock(Observability.class);

        when(chatClientBuilder.build()).thenReturn(statelessChatClient);
        when(statelessChatClient.prompt()).thenReturn(statelessSpec);
        when(statelessSpec.user(anyString())).thenReturn(statelessSpec);
        when(statelessSpec.call()).thenReturn(callResponseSpec);

        service = new ChatService(memoryChatClient, chatClientBuilder, observability);
    }

    @Test
    @DisplayName("调用成功时记录成功观测")
    void should_observeSuccess_when_callSucceeds() {
        when(callResponseSpec.content()).thenReturn("正常回复");

        service.chat("你好");

        verify(observability).recordLlmCall(eq(true), any(long.class), eq("正常回复"));
    }

    @Test
    @DisplayName("调用失败时记录失败观测并抛出 LlmCallException")
    void should_observeFailure_when_callFails() {
        when(callResponseSpec.content()).thenThrow(new IllegalStateException("timeout"));

        assertThatThrownBy(() -> service.chat("你好"))
                .isInstanceOf(LlmCallException.class);

        verify(observability).recordLlmCall(eq(false), any(long.class),
                eq("LLM 调用失败: timeout"));
    }

    @Test
    @DisplayName("旧构造器（无 observability）调用正常且不记录")
    void should_workWithoutObservability_when_twoArgConstructor() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(statelessChatClient);
        when(callResponseSpec.content()).thenReturn("回复");
        ChatService plain = new ChatService(memoryChatClient, builder);

        plain.chat("你好");

        verify(observability, org.mockito.Mockito.never())
                .recordLlmCall(any(Boolean.class), any(long.class), any());
    }
}
