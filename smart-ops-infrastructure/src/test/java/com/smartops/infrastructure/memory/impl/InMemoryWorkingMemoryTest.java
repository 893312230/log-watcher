package com.smartops.infrastructure.memory.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link InMemoryWorkingMemory} 单元测试。
 *
 * <p>验证工作记忆的核心契约（ADR-014 决策3）：
 * 按 conversationId 隔离的 KV 读写、每会话条目数有界淘汰、
 * 全局会话数有界淘汰、任务结束清理、参数校验。
 * 进程内实现，不持久化。</p>
 *
 * <p><b>测试策略</b>：纯内存实现，无需 Mock；AssertJ 断言；
 * 测试方法命名 {@code should_{期望行为}_when_{前置条件}}。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class InMemoryWorkingMemoryTest {

    @Nested
    @DisplayName("读写与隔离")
    class ReadWriteIsolation {

        @Test
        @DisplayName("写入后可按 conversationId + key 读回")
        void should_readBackValue_when_putThenGet() {
            InMemoryWorkingMemory memory = new InMemoryWorkingMemory(10, 10);

            memory.put("conv-1", "react.user-input", "查询 CPU");

            assertThat(memory.get("conv-1", "react.user-input")).contains("查询 CPU");
        }

        @Test
        @DisplayName("不同 conversationId 之间相互隔离，同 key 不串值")
        void should_isolateValues_when_differentConversations() {
            InMemoryWorkingMemory memory = new InMemoryWorkingMemory(10, 10);
            memory.put("conv-1", "k", "v1");
            memory.put("conv-2", "k", "v2");

            assertThat(memory.get("conv-1", "k")).contains("v1");
            assertThat(memory.get("conv-2", "k")).contains("v2");
        }

        @Test
        @DisplayName("读取不存在的 key 返回 Optional.empty")
        void should_returnEmpty_when_keyMissing() {
            InMemoryWorkingMemory memory = new InMemoryWorkingMemory(10, 10);

            assertThat(memory.get("conv-1", "missing")).isEmpty();
        }

        @Test
        @DisplayName("读取不存在的会话返回 Optional.empty")
        void should_returnEmpty_when_conversationMissing() {
            InMemoryWorkingMemory memory = new InMemoryWorkingMemory(10, 10);

            Optional<String> value = memory.get("no-such-conv", "k");

            assertThat(value).isEmpty();
        }

        @Test
        @DisplayName("同 key 重复写入覆盖旧值")
        void should_overwriteValue_when_putSameKeyTwice() {
            InMemoryWorkingMemory memory = new InMemoryWorkingMemory(10, 10);
            memory.put("conv-1", "k", "old");

            memory.put("conv-1", "k", "new");

            assertThat(memory.get("conv-1", "k")).contains("new");
        }
    }

    @Nested
    @DisplayName("有界淘汰")
    class BoundedEviction {

        @Test
        @DisplayName("单会话条目数超过 maxEntries 时淘汰最久未访问的条目")
        void should_evictEldestEntry_when_conversationExceedsMaxEntries() {
            InMemoryWorkingMemory memory = new InMemoryWorkingMemory(2, 10);
            memory.put("conv-1", "k1", "v1");
            memory.put("conv-1", "k2", "v2");

            memory.put("conv-1", "k3", "v3");

            assertThat(memory.get("conv-1", "k1")).isEmpty();
            assertThat(memory.get("conv-1", "k2")).contains("v2");
            assertThat(memory.get("conv-1", "k3")).contains("v3");
        }

        @Test
        @DisplayName("访问会刷新条目热度，被访问的条目不被淘汰")
        void should_keepRecentlyAccessedEntry_when_evictionTriggered() {
            InMemoryWorkingMemory memory = new InMemoryWorkingMemory(2, 10);
            memory.put("conv-1", "k1", "v1");
            memory.put("conv-1", "k2", "v2");
            // 访问 k1 刷新热度，使 k2 成为最久未访问
            memory.get("conv-1", "k1");

            memory.put("conv-1", "k3", "v3");

            assertThat(memory.get("conv-1", "k1")).contains("v1");
            assertThat(memory.get("conv-1", "k2")).isEmpty();
            assertThat(memory.get("conv-1", "k3")).contains("v3");
        }

        @Test
        @DisplayName("会话总数超过 maxConversations 时淘汰最久未访问的会话")
        void should_evictEldestConversation_when_exceedsMaxConversations() {
            InMemoryWorkingMemory memory = new InMemoryWorkingMemory(10, 2);
            memory.put("conv-1", "k", "v1");
            memory.put("conv-2", "k", "v2");

            memory.put("conv-3", "k", "v3");

            assertThat(memory.get("conv-1", "k")).isEmpty();
            assertThat(memory.get("conv-2", "k")).contains("v2");
            assertThat(memory.get("conv-3", "k")).contains("v3");
        }

        @Test
        @DisplayName("访问会话会刷新会话热度，被访问的会话不被淘汰")
        void should_keepRecentlyAccessedConversation_when_conversationEvictionTriggered() {
            InMemoryWorkingMemory memory = new InMemoryWorkingMemory(10, 2);
            memory.put("conv-1", "k", "v1");
            memory.put("conv-2", "k", "v2");
            // 访问 conv-1 刷新热度，使 conv-2 成为最久未访问
            memory.get("conv-1", "k");

            memory.put("conv-3", "k", "v3");

            assertThat(memory.get("conv-1", "k")).contains("v1");
            assertThat(memory.get("conv-2", "k")).isEmpty();
            assertThat(memory.get("conv-3", "k")).contains("v3");
        }
    }

    @Nested
    @DisplayName("清理")
    class Cleanup {

        @Test
        @DisplayName("clear 后该会话所有条目被移除")
        void should_removeAllEntries_when_clearConversation() {
            InMemoryWorkingMemory memory = new InMemoryWorkingMemory(10, 10);
            memory.put("conv-1", "k1", "v1");
            memory.put("conv-1", "k2", "v2");

            memory.clear("conv-1");

            assertThat(memory.get("conv-1", "k1")).isEmpty();
            assertThat(memory.get("conv-1", "k2")).isEmpty();
        }

        @Test
        @DisplayName("clear 不影响其他会话")
        void should_notAffectOtherConversations_when_clear() {
            InMemoryWorkingMemory memory = new InMemoryWorkingMemory(10, 10);
            memory.put("conv-1", "k", "v1");
            memory.put("conv-2", "k", "v2");

            memory.clear("conv-1");

            assertThat(memory.get("conv-2", "k")).contains("v2");
        }

        @Test
        @DisplayName("clear 不存在的会话不抛异常")
        void should_notThrow_when_clearMissingConversation() {
            InMemoryWorkingMemory memory = new InMemoryWorkingMemory(10, 10);

            memory.clear("no-such-conv");
        }
    }

    @Nested
    @DisplayName("参数校验")
    class Validation {

        @Test
        @DisplayName("maxEntries 非正数时构造抛出 IllegalArgumentException")
        void should_throwIllegalArgument_when_maxEntriesNonPositive() {
            assertThatThrownBy(() -> new InMemoryWorkingMemory(0, 10))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxEntries");
        }

        @Test
        @DisplayName("maxConversations 非正数时构造抛出 IllegalArgumentException")
        void should_throwIllegalArgument_when_maxConversationsNonPositive() {
            assertThatThrownBy(() -> new InMemoryWorkingMemory(10, -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxConversations");
        }

        @Test
        @DisplayName("conversationId 为 null 或空白时 put 抛出 IllegalArgumentException")
        void should_throwIllegalArgument_when_putWithBlankConversationId() {
            InMemoryWorkingMemory memory = new InMemoryWorkingMemory(10, 10);

            assertThatThrownBy(() -> memory.put(null, "k", "v"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> memory.put("  ", "k", "v"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("key 或 value 为 null 时 put 抛出 IllegalArgumentException")
        void should_throwIllegalArgument_when_putWithNullKeyOrValue() {
            InMemoryWorkingMemory memory = new InMemoryWorkingMemory(10, 10);

            assertThatThrownBy(() -> memory.put("conv-1", null, "v"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> memory.put("conv-1", "k", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
