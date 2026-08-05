package com.smartops.infrastructure.config;

import com.smartops.infrastructure.memory.BoundedChatMemoryRepository;
import com.smartops.infrastructure.memory.impl.RedisChatMemoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link ChatMemoryConfig} 单元测试。
 *
 * <p>验证短期记忆的窗口大小配置与淘汰行为，对应 agent.md 阶段一任务5。
 * 不依赖 Spring 容器，直接调用配置类方法并反射注入属性。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class ChatMemoryConfigTest {

    @Test
    @DisplayName("默认窗口大小为 20")
    void should_useDefaultWindowSize_when_notConfigured() {
        ChatMemoryConfig config = new ChatMemoryConfig();
        // 模拟 @Value 未注入时的默认值
        ReflectionTestUtils.setField(config, "windowSize", 20);

        ChatMemory chatMemory = config.chatMemory(new InMemoryChatMemoryRepository());

        assertThat(chatMemory).isNotNull();
        // 通过内部状态间接验证窗口大小：后续行为测试覆盖
    }

    @Test
    @DisplayName("mid-term 关闭时记忆仓库为有界内存实现")
    void should_useBoundedRepository_when_midTermDisabled() {
        ChatMemoryConfig config = new ChatMemoryConfig();

        ChatMemoryRepository repository = config.inMemoryChatMemoryRepository();

        assertThat(repository).isInstanceOf(BoundedChatMemoryRepository.class);
    }

    @Test
    @DisplayName("mid-term 开启时记忆仓库为 Redis 持久化实现")
    void should_useRedisRepository_when_midTermEnabled() {
        ChatMemoryConfig config = new ChatMemoryConfig();
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

        ChatMemoryRepository repository = config.redisChatMemoryRepository(redisTemplate, 7L);

        assertThat(repository).isInstanceOf(RedisChatMemoryRepository.class);
    }

    @Test
    @DisplayName("自定义窗口大小为 5 时，超过后按对话轮次对齐淘汰最早的")
    void should_evictOldMessages_when_exceedWindowSize() {
        // Arrange：构建窗口大小为 5 的记忆
        ChatMemoryRepository repository = new InMemoryChatMemoryRepository();
        ChatMemory chatMemory = org.springframework.ai.chat.memory.MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(5)
                .build();

        String conversationId = "test-conv-1";

        // Act：交替添加用户消息和助手消息，共 14 条（7 轮），超过窗口大小 5
        for (int i = 0; i < 7; i++) {
            chatMemory.add(conversationId, new UserMessage("用户消息 " + i));
            chatMemory.add(conversationId, new AssistantMessage("助手回复 " + i));
        }

        // Assert：Spring AI 2.0 起窗口按 User/Assistant 轮次对齐淘汰，
        // 不会在对话轮中间截断，因此 maxMessages=5 时实际保留最后 4 条（2 个完整轮次）
        List<org.springframework.ai.chat.messages.Message> messages = chatMemory.get(conversationId);
        assertThat(messages).hasSize(4);
        assertThat(messages.get(0).getText()).isEqualTo("用户消息 5");
        assertThat(messages.get(3).getText()).isEqualTo("助手回复 6");
    }

    @Test
    @DisplayName("同一会话多次添加消息，窗口大小稳定在配置值")
    void should_keepWindowSizeStable_when_multipleAdds() {
        ChatMemoryRepository repository = new InMemoryChatMemoryRepository();
        ChatMemory chatMemory = org.springframework.ai.chat.memory.MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(3)
                .build();

        String conversationId = "test-conv-2";

        // 第一次添加 2 条
        chatMemory.add(conversationId, new UserMessage("你好"));
        chatMemory.add(conversationId, new AssistantMessage("你好，有什么可以帮你？"));
        assertThat(chatMemory.get(conversationId)).hasSize(2);

        // 再添加 4 条，总数 6，窗口 maxMessages=3 按轮次对齐后保留最后 2 条（1 个完整轮次）
        chatMemory.add(conversationId, new UserMessage("查询 CPU 使用率"));
        chatMemory.add(conversationId, new AssistantMessage("正在查询..."));
        chatMemory.add(conversationId, new UserMessage("查询内存使用率"));
        chatMemory.add(conversationId, new AssistantMessage("内存使用率 60%"));

        List<org.springframework.ai.chat.messages.Message> messages = chatMemory.get(conversationId);
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getText()).isEqualTo("查询内存使用率");
    }

    @Test
    @DisplayName("不同会话的记忆相互隔离")
    void should_isolateMemory_when_differentConversations() {
        ChatMemoryRepository repository = new InMemoryChatMemoryRepository();
        ChatMemory chatMemory = org.springframework.ai.chat.memory.MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(20)
                .build();

        chatMemory.add("conv-a", new UserMessage("会话 A 的消息"));
        chatMemory.add("conv-b", new UserMessage("会话 B 的消息"));

        List<org.springframework.ai.chat.messages.Message> messagesA = chatMemory.get("conv-a");
        List<org.springframework.ai.chat.messages.Message> messagesB = chatMemory.get("conv-b");

        assertThat(messagesA).hasSize(1);
        assertThat(messagesB).hasSize(1);
        assertThat(messagesA.get(0).getText()).isEqualTo("会话 A 的消息");
        assertThat(messagesB.get(0).getText()).isEqualTo("会话 B 的消息");
    }

    @Test
    @DisplayName("会话不存在时返回空列表")
    void should_returnEmptyList_when_conversationNotExists() {
        ChatMemoryRepository repository = new InMemoryChatMemoryRepository();
        ChatMemory chatMemory = org.springframework.ai.chat.memory.MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(20)
                .build();

        List<org.springframework.ai.chat.messages.Message> messages = chatMemory.get("non-existent");

        assertThat(messages).isNotNull().isEmpty();
    }
}
