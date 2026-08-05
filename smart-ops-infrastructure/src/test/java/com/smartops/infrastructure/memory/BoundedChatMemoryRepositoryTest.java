package com.smartops.infrastructure.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link BoundedChatMemoryRepository} 单元测试。
 *
 * <p>验证有界记忆仓库的 LRU 淘汰、CRUD 与输入校验，
 * 防止会话数无界增长导致 OOM。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class BoundedChatMemoryRepositoryTest {

    @Test
    @DisplayName("超过最大会话数时淘汰最久未访问的会话")
    void should_evictLeastRecentlyUsed_when_exceedMaxConversations() {
        BoundedChatMemoryRepository repository = new BoundedChatMemoryRepository(2);

        repository.saveAll("conv-1", List.of(new UserMessage("消息1")));
        repository.saveAll("conv-2", List.of(new UserMessage("消息2")));
        // conv-1 变为最久未访问，保存 conv-3 时应淘汰 conv-1
        repository.saveAll("conv-3", List.of(new UserMessage("消息3")));

        assertThat(repository.findConversationIds()).containsExactlyInAnyOrder("conv-2", "conv-3");
        assertThat(repository.findByConversationId("conv-1")).isEmpty();
    }

    @Test
    @DisplayName("读取会话会刷新其访问热度，避免被淘汰")
    void should_refreshRecency_when_conversationAccessed() {
        BoundedChatMemoryRepository repository = new BoundedChatMemoryRepository(2);

        repository.saveAll("conv-1", List.of(new UserMessage("消息1")));
        repository.saveAll("conv-2", List.of(new UserMessage("消息2")));
        // 访问 conv-1 刷新热度，conv-2 变为最久未访问
        repository.findByConversationId("conv-1");
        repository.saveAll("conv-3", List.of(new UserMessage("消息3")));

        assertThat(repository.findConversationIds()).containsExactlyInAnyOrder("conv-1", "conv-3");
    }

    @Test
    @DisplayName("重复保存同一会话覆盖旧消息且不触发淘汰")
    void should_overwrite_when_saveSameConversationTwice() {
        BoundedChatMemoryRepository repository = new BoundedChatMemoryRepository(2);

        repository.saveAll("conv-1", List.of(new UserMessage("旧消息")));
        repository.saveAll("conv-1", List.of(new UserMessage("新消息"), new AssistantMessage("回复")));

        assertThat(repository.findByConversationId("conv-1")).hasSize(2);
        assertThat(repository.findConversationIds()).hasSize(1);
    }

    @Test
    @DisplayName("删除会话后不可再查询")
    void should_removeConversation_when_deleteById() {
        BoundedChatMemoryRepository repository = new BoundedChatMemoryRepository(10);

        repository.saveAll("conv-1", List.of(new UserMessage("消息")));
        repository.deleteByConversationId("conv-1");

        assertThat(repository.findByConversationId("conv-1")).isEmpty();
        assertThat(repository.findConversationIds()).isEmpty();
    }

    @Test
    @DisplayName("查询不存在的会话返回空列表")
    void should_returnEmptyList_when_conversationNotExists() {
        BoundedChatMemoryRepository repository = new BoundedChatMemoryRepository(10);

        assertThat(repository.findByConversationId("不存在")).isEmpty();
    }

    @Test
    @DisplayName("最大会话数必须为正数")
    void should_throwIllegalArg_when_maxConversationsNotPositive() {
        assertThatThrownBy(() -> new BoundedChatMemoryRepository(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BoundedChatMemoryRepository(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
