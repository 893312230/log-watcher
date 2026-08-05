package com.smartops.infrastructure.chat;

import com.smartops.infrastructure.memory.BoundedChatMemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 会话记忆隔离集成测试（IT-1）。
 *
 * <p>使用真实的 MessageChatMemoryAdvisor + MessageWindowChatMemory + BoundedChatMemoryRepository
 * 构建 Advisor 链，仅 Mock ChatModel（不发真实 LLM 请求），端到端验证：
 * <ol>
 *   <li>不同 conversationId 的记忆严格隔离（修复跨用户上下文泄露 P0 缺陷）</li>
 *   <li>同一会话的后续调用能看到历史消息</li>
 *   <li>无状态调用不写入任何会话记忆</li>
 * </ol></p>
 *
 * @author smartops
 * @since 1.0.0
 */
class ChatServiceMemoryIsolationTest {

    private ChatMemory chatMemory;
    private ChatService chatService;
    private List<Prompt> capturedPrompts;

    @BeforeEach
    void setUp() {
        capturedPrompts = new ArrayList<>();

        // Mock ChatModel：回显用户消息，记录每次收到的 Prompt
        // Spring AI 2.0 的 ChatClient 会调用 chatModel.getOptions().mutate()，需返回非 null
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            capturedPrompts.add(prompt);
            return new ChatResponse(List.of(
                    new Generation(new AssistantMessage("回复: " + prompt.getUserMessage().getText()))));
        });

        chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new BoundedChatMemoryRepository(100))
                .maxMessages(20)
                .build();

        ChatClient memoryClient = ChatClient.builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();

        chatService = new ChatService(memoryClient, ChatClient.builder(chatModel));
    }

    @Test
    @DisplayName("不同会话的记忆严格隔离，互不泄露")
    void should_isolateMemory_when_differentConversations() {
        chatService.chat("user-a", "查询A的CPU");
        chatService.chat("user-b", "重启B的服务");

        List<Message> memoryA = chatMemory.get("user-a");
        List<Message> memoryB = chatMemory.get("user-b");

        // user-a 的记忆只包含自己的消息与回复
        assertThat(memoryA).hasSize(2);
        assertThat(memoryA.get(0).getText()).contains("查询A的CPU");
        assertThat(memoryA.toString()).doesNotContain("重启B的服务");
        // user-b 的记忆只包含自己的消息与回复
        assertThat(memoryB).hasSize(2);
        assertThat(memoryB.get(0).getText()).contains("重启B的服务");
        assertThat(memoryB.toString()).doesNotContain("查询A的CPU");
    }

    @Test
    @DisplayName("同一会话的后续调用携带历史消息")
    void should_includeHistory_when_sameConversationContinues() {
        chatService.chat("user-c", "第一条消息");
        chatService.chat("user-c", "第二条消息");

        // 第二次调用时 Advisor 应已加载第一轮历史
        Prompt secondPrompt = capturedPrompts.get(1);
        assertThat(secondPrompt.getContents()).contains("第一条消息");
    }

    @Test
    @DisplayName("无状态调用不写入任何会话记忆")
    void should_notPersistMemory_when_statelessCall() {
        chatService.chat("user-d", "会话消息");
        chatService.chat("这是一次元调用");
        chatService.chatWithSystemPrompt("系统提示", "这是另一次元调用");

        // 只有显式会话调用产生记忆，元调用不创建任何新会话
        assertThat(chatMemory.get("user-d")).hasSize(2);
        // 元调用内容不出现在任何会话记忆中
        assertThat(chatMemory.get("user-d").toString()).doesNotContain("元调用");
    }
}
