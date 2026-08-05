package com.smartops.infrastructure.chat;

import com.smartops.common.exception.LlmCallException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ChatService} 单元测试。
 *
 * <p>验证双客户端设计：无状态调用（元调用）路由到无记忆客户端，
 * 会话级调用路由到记忆客户端并设置 CONVERSATION_ID advisor 参数，
 * 从机制上保证会话记忆隔离（修复跨用户上下文泄露缺陷）。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class ChatServiceTest {

    private ChatClient memoryChatClient;
    private ChatClient statelessChatClient;
    private ChatClient.Builder chatClientBuilder;
    private ChatClient.ChatClientRequestSpec memorySpec;
    private ChatClient.ChatClientRequestSpec statelessSpec;
    private ChatClient.CallResponseSpec callResponseSpec;
    private ChatService service;

    @BeforeEach
    void setUp() {
        memoryChatClient = mock(ChatClient.class);
        statelessChatClient = mock(ChatClient.class);
        chatClientBuilder = mock(ChatClient.Builder.class);
        memorySpec = mock(ChatClient.ChatClientRequestSpec.class);
        statelessSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClientBuilder.build()).thenReturn(statelessChatClient);

        // 记忆客户端链：prompt() → user/system/tools/advisors → call() → content()
        when(memoryChatClient.prompt()).thenReturn(memorySpec);
        when(memorySpec.user(anyString())).thenReturn(memorySpec);
        when(memorySpec.system(anyString())).thenReturn(memorySpec);
        when(memorySpec.tools(any(Object[].class))).thenReturn(memorySpec);
        when(memorySpec.advisors(any(Consumer.class))).thenReturn(memorySpec);
        when(memorySpec.call()).thenReturn(callResponseSpec);

        // 无记忆客户端链
        when(statelessChatClient.prompt()).thenReturn(statelessSpec);
        when(statelessSpec.user(anyString())).thenReturn(statelessSpec);
        when(statelessSpec.system(anyString())).thenReturn(statelessSpec);
        when(statelessSpec.tools(any(Object[].class))).thenReturn(statelessSpec);
        when(statelessSpec.call()).thenReturn(callResponseSpec);

        when(callResponseSpec.content()).thenReturn("模拟回复");

        service = new ChatService(memoryChatClient, chatClientBuilder);
    }

    @Nested
    @DisplayName("无状态调用（元调用）")
    class StatelessCalls {

        @Test
        @DisplayName("chat 单参路由到无记忆客户端")
        void should_useStatelessClient_when_chatWithoutConversationId() {
            String result = service.chat("查询 CPU 使用率");

            assertThat(result).isEqualTo("模拟回复");
            verify(statelessSpec).user("查询 CPU 使用率");
        }

        @Test
        @DisplayName("chatWithTools 不带会话 ID 路由到无记忆客户端")
        void should_useStatelessClient_when_chatWithToolsWithoutConversationId() {
            Object toolBean = new Object();

            String result = service.chatWithTools("重启服务", toolBean);

            assertThat(result).isEqualTo("模拟回复");
            verify(statelessSpec).tools(toolBean);
        }

        @Test
        @DisplayName("chatWithTools 工具数组为 null 时不调用 tools")
        void should_notCallTools_when_toolBeansNull() {
            String result = service.chatWithTools("查询指标", (Object[]) null);

            assertThat(result).isEqualTo("模拟回复");
        }

        @Test
        @DisplayName("chatWithTools 工具数组为空时不调用 tools")
        void should_notCallTools_when_toolBeansEmpty() {
            String result = service.chatWithTools("查询指标");

            assertThat(result).isEqualTo("模拟回复");
        }

        @Test
        @DisplayName("chatWithSystemPrompt 路由到无记忆客户端并设置系统提示词")
        void should_useStatelessClient_when_chatWithSystemPrompt() {
            String result = service.chatWithSystemPrompt("你是运维助手", "查询 CPU");

            assertThat(result).isEqualTo("模拟回复");
            verify(statelessSpec).system("你是运维助手");
            verify(statelessSpec).user("查询 CPU");
        }

        @Test
        @DisplayName("chatWithSystemPrompt 带工具时注册工具")
        void should_registerTools_when_chatWithSystemPromptWithTools() {
            Object toolBean = new Object();

            service.chatWithSystemPrompt("你是运维助手", "查询 CPU", toolBean);

            verify(statelessSpec).tools(toolBean);
        }

        @Test
        @DisplayName("chatWithSystemPrompt 工具数组为 null 时不调用 tools")
        void should_notCallTools_when_systemPromptToolBeansNull() {
            String result = service.chatWithSystemPrompt("你是运维助手", "查询 CPU", (Object[]) null);

            assertThat(result).isEqualTo("模拟回复");
        }

        @Test
        @DisplayName("chatWithSystemPrompt 工具数组为空时不调用 tools")
        void should_notCallTools_when_systemPromptToolBeansEmpty() {
            String result = service.chatWithSystemPrompt("你是运维助手", "查询 CPU");

            assertThat(result).isEqualTo("模拟回复");
        }

        @Test
        @DisplayName("LLM 返回 null 时透传 null")
        void should_returnNull_when_llmReturnsNull() {
            when(callResponseSpec.content()).thenReturn(null);

            assertThat(service.chat("hello")).isNull();
        }
    }

    @Nested
    @DisplayName("会话级调用（记忆隔离）")
    class ConversationCalls {

        @Test
        @DisplayName("chat 带会话 ID 路由到记忆客户端")
        void should_useMemoryClient_when_chatWithConversationId() {
            String result = service.chat("conv-1", "查询 CPU");

            assertThat(result).isEqualTo("模拟回复");
            verify(memorySpec).user("查询 CPU");
        }

        @Test
        @DisplayName("会话级调用设置 CONVERSATION_ID advisor 参数")
        @SuppressWarnings("unchecked")
        void should_setConversationIdParam_when_conversationCall() {
            service.chat("conv-42", "查询内存");

            ArgumentCaptor<Consumer<ChatClient.AdvisorSpec>> captor =
                    ArgumentCaptor.forClass(Consumer.class);
            verify(memorySpec).advisors(captor.capture());

            ChatClient.AdvisorSpec advisorSpec = mock(ChatClient.AdvisorSpec.class);
            captor.getValue().accept(advisorSpec);
            verify(advisorSpec).param(ChatMemory.CONVERSATION_ID, "conv-42");
        }

        @Test
        @DisplayName("chatWithTools 带会话 ID 注册工具并设置会话参数")
        void should_registerTools_when_conversationChatWithTools() {
            Object toolBean = new Object();

            service.chatWithTools("conv-2", "重启服务", toolBean);

            verify(memorySpec).tools(toolBean);
        }

        @Test
        @DisplayName("chatWithTools 带会话 ID 工具数组为 null 时不调用 tools")
        void should_notCallTools_when_conversationToolBeansNull() {
            String result = service.chatWithTools("conv-3", "查询指标", (Object[]) null);

            assertThat(result).isEqualTo("模拟回复");
        }

        @Test
        @DisplayName("chatWithTools 带会话 ID 工具数组为空时不调用 tools")
        void should_notCallTools_when_conversationToolBeansEmpty() {
            String result = service.chatWithTools("conv-3", "查询指标");

            assertThat(result).isEqualTo("模拟回复");
        }

        @Test
        @DisplayName("chat 带会话 ID 和系统提示词时设置系统提示词")
        void should_setSystemPrompt_when_conversationChatWithSystemPrompt() {
            service.chat("conv-4", "你是运维助手", "查询 CPU");

            verify(memorySpec).system("你是运维助手");
            verify(memorySpec).user("查询 CPU");
        }

        @Test
        @DisplayName("chat 三参带工具时注册工具")
        void should_registerTools_when_conversationChatThreeArgWithTools() {
            Object toolBean = new Object();

            service.chat("conv-5", "你是运维助手", "查询 CPU", toolBean);

            verify(memorySpec).tools(toolBean);
        }

        @Test
        @DisplayName("chat 三参工具数组为 null 时不调用 tools")
        void should_notCallTools_when_conversationChatThreeArgToolBeansNull() {
            String result = service.chat("conv-5", "你是运维助手", "查询 CPU", (Object[]) null);

            assertThat(result).isEqualTo("模拟回复");
        }
    }

    @Nested
    @DisplayName("输入校验")
    class InputValidation {

        @Test
        @DisplayName("chat 消息为 null 时抛出异常")
        void should_throwIllegalArg_when_chatMessageNull() {
            assertThatThrownBy(() -> service.chat(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("消息不能为 null");
        }

        @Test
        @DisplayName("chat 消息为空白时抛出异常")
        void should_throwIllegalArg_when_chatMessageBlank() {
            assertThatThrownBy(() -> service.chat("  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("chatWithTools 消息为 null 时抛出异常")
        void should_throwIllegalArg_when_chatWithToolsMessageNull() {
            assertThatThrownBy(() -> service.chatWithTools(null, new Object()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("chatWithSystemPrompt 系统提示为 null 时抛出异常")
        void should_throwIllegalArg_when_systemPromptNull() {
            assertThatThrownBy(() -> service.chatWithSystemPrompt(null, "查询 CPU"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("chatWithSystemPrompt 系统提示为空白时抛出异常")
        void should_throwIllegalArg_when_systemPromptBlank() {
            assertThatThrownBy(() -> service.chatWithSystemPrompt("  ", "查询 CPU"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("会话级 chat 会话 ID 为 null 时抛出异常")
        void should_throwIllegalArg_when_conversationIdNull() {
            assertThatThrownBy(() -> service.chat(null, "查询 CPU"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("会话 ID 不能为 null");
        }

        @Test
        @DisplayName("会话级 chat 会话 ID 为空白时抛出异常")
        void should_throwIllegalArg_when_conversationIdBlank() {
            assertThatThrownBy(() -> service.chat("  ", "查询 CPU"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("会话级 chatWithTools 会话 ID 为 null 时抛出异常")
        void should_throwIllegalArg_when_conversationChatWithToolsIdNull() {
            assertThatThrownBy(() -> service.chatWithTools(null, "查询", new Object()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("会话级 chat 三参会话 ID 为空白时抛出异常")
        void should_throwIllegalArg_when_conversationChatThreeArgIdBlank() {
            assertThatThrownBy(() -> service.chat("", "系统提示", "查询"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("异常翻译")
    class ExceptionTranslation {

        @Test
        @DisplayName("LLM 调用抛原始运行时异常时包装为 LlmCallException 并保留根因")
        void should_wrapRuntimeException_when_llmCallFails() {
            when(callResponseSpec.content()).thenThrow(new IllegalStateException("HTTP 503"));

            assertThatThrownBy(() -> service.chat("查询 CPU"))
                    .isInstanceOf(LlmCallException.class)
                    .hasMessageContaining("HTTP 503")
                    .hasCauseInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("会话级调用异常同样被包装")
        void should_wrapRuntimeException_when_conversationCallFails() {
            when(callResponseSpec.content()).thenThrow(new IllegalStateException("连接超时"));

            assertThatThrownBy(() -> service.chat("conv-1", "查询 CPU"))
                    .isInstanceOf(LlmCallException.class)
                    .hasMessageContaining("连接超时");
        }

        @Test
        @DisplayName("已是 LlmCallException 时直接重抛，不做二次包装")
        void should_rethrowAsIs_when_alreadyLlmCallException() {
            when(callResponseSpec.content())
                    .thenThrow(new LlmCallException("LLM 调用失败: 工具执行异常"));

            assertThatThrownBy(() -> service.chatWithSystemPrompt("系统提示", "查询 CPU"))
                    .isInstanceOf(LlmCallException.class)
                    .hasMessage("LLM 调用失败: 工具执行异常")
                    .hasNoCause();
        }

        @Test
        @DisplayName("包装后的异常错误码固定为 LLM_CALL_FAILED")
        void should_carryFixedErrorCode_when_wrapped() {
            when(callResponseSpec.content()).thenThrow(new IllegalStateException("HTTP 503"));

            assertThatThrownBy(() -> service.chatWithTools("查询指标", new Object()))
                    .isInstanceOfSatisfying(LlmCallException.class, ex ->
                            assertThat(ex.getErrorCode()).isEqualTo(LlmCallException.ERROR_CODE));
        }
    }
}
