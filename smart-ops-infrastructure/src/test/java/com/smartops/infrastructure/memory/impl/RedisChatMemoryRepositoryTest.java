package com.smartops.infrastructure.memory.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RedisChatMemoryRepository} 单元测试。
 *
 * <p>验证中期记忆 Redis 仓库的核心契约（ADR-014）：
 * <ul>
 *   <li>消息以 {type, text, metadata} JSON 三元组覆盖式存储，key 带 smartops:memory: 前缀</li>
 *   <li>写入设置 TTL，读取命中时刷新 TTL（会话跨重启存活、自动过期）</li>
 *   <li>USER/ASSISTANT/SYSTEM 类型忠实还原；TOOL 类型加载时过滤</li>
 *   <li>单条会话数据损坏（JSON 无法解析）时降级为空会话，不抛出异常</li>
 *   <li>会话 id 列举去除 key 前缀并按字典序返回</li>
 * </ul></p>
 *
 * @author smartops
 * @since 1.0.0
 */
class RedisChatMemoryRepositoryTest {

    private static final Duration TTL = Duration.ofDays(7);
    private static final String KEY = "smartops:memory:conv-1";

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private RedisChatMemoryRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        repository = new RedisChatMemoryRepository(redisTemplate, TTL);
    }

    /**
     * 保存消息并捕获写入 Redis 的 JSON，同时校验 key 与 TTL。
     *
     * @param messages 待保存消息
     * @return 解析后的 JSON 节点
     * @throws Exception JSON 解析异常
     */
    private JsonNode saveAndCapture(List<Message> messages) throws Exception {
        repository.saveAll("conv-1", messages);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq(KEY), jsonCaptor.capture(), eq(TTL));
        return objectMapper.readTree(jsonCaptor.getValue());
    }

    @Nested
    @DisplayName("读写往返")
    class RoundTrip {

        @Test
        @DisplayName("保存后按类型还原 USER/ASSISTANT/SYSTEM 消息")
        void should_restoreMessagesByType_when_savedAndLoaded() throws Exception {
            JsonNode json = saveAndCapture(List.of(
                    new UserMessage("查询 CPU 使用率"),
                    new AssistantMessage("CPU 使用率 42%"),
                    new SystemMessage("你是运维助手")));

            assertThat(json).hasSize(3);
            assertThat(json.get(0).get("type").asText()).isEqualTo("USER");
            assertThat(json.get(0).get("text").asText()).isEqualTo("查询 CPU 使用率");
            assertThat(json.get(1).get("type").asText()).isEqualTo("ASSISTANT");
            assertThat(json.get(2).get("type").asText()).isEqualTo("SYSTEM");

            when(valueOps.get(KEY)).thenReturn(objectMapper.writeValueAsString(json));
            List<Message> loaded = repository.findByConversationId("conv-1");

            assertThat(loaded).hasSize(3);
            assertThat(loaded.get(0).getMessageType()).isEqualTo(MessageType.USER);
            assertThat(loaded.get(0).getText()).isEqualTo("查询 CPU 使用率");
            assertThat(loaded.get(1).getMessageType()).isEqualTo(MessageType.ASSISTANT);
            assertThat(loaded.get(2).getMessageType()).isEqualTo(MessageType.SYSTEM);
        }

        @Test
        @DisplayName("读取命中时刷新 TTL")
        void should_refreshTtl_when_hit() {
            when(valueOps.get(KEY)).thenReturn("[]");

            repository.findByConversationId("conv-1");

            verify(redisTemplate).expire(KEY, TTL);
        }

        @Test
        @DisplayName("会话不存在时返回空表且不刷新 TTL")
        void should_returnEmptyAndNotRefreshTtl_when_miss() {
            when(valueOps.get(KEY)).thenReturn(null);

            List<Message> messages = repository.findByConversationId("conv-1");

            assertThat(messages).isEmpty();
            verify(redisTemplate, never()).expire(eq(KEY), org.mockito.ArgumentMatchers.any(Duration.class));
        }
    }

    @Nested
    @DisplayName("会话管理")
    class ConversationManagement {

        @Test
        @DisplayName("列举会话 id 时去除 key 前缀并按字典序返回")
        void should_stripPrefixAndSort_when_listingConversationIds() {
            when(redisTemplate.keys("smartops:memory:*")).thenReturn(Set.of(
                    "smartops:memory:conv-b", "smartops:memory:conv-a"));

            List<String> ids = repository.findConversationIds();

            assertThat(ids).containsExactly("conv-a", "conv-b");
        }

        @Test
        @DisplayName("无任何 key 时返回空表")
        void should_returnEmpty_when_noKeys() {
            when(redisTemplate.keys("smartops:memory:*")).thenReturn(null);

            assertThat(repository.findConversationIds()).isEmpty();
        }

        @Test
        @DisplayName("删除会话时按前缀 key 删除")
        void should_deleteByPrefixedKey_when_deleteConversation() {
            repository.deleteByConversationId("conv-1");

            verify(redisTemplate).delete(KEY);
        }
    }

    @Nested
    @DisplayName("降级语义")
    class Degradation {

        @Test
        @DisplayName("JSON 损坏时降级为空会话且不抛出异常")
        void should_degradeToEmpty_when_jsonCorrupted() {
            when(valueOps.get(KEY)).thenReturn("{not-valid-json");

            List<Message> messages = repository.findByConversationId("conv-1");

            assertThat(messages).isEmpty();
        }

        @Test
        @DisplayName("TOOL 类型消息加载时过滤")
        void should_filterToolMessages_when_loading() {
            String json = """
                    [
                      {"type":"USER","text":"查询指标","metadata":{}},
                      {"type":"TOOL","text":"tool-response","metadata":{}},
                      {"type":"ASSISTANT","text":"CPU 42%","metadata":{}}
                    ]
                    """;
            when(valueOps.get(KEY)).thenReturn(json);

            List<Message> messages = repository.findByConversationId("conv-1");

            assertThat(messages).hasSize(2);
            assertThat(messages.get(0).getMessageType()).isEqualTo(MessageType.USER);
            assertThat(messages.get(1).getMessageType()).isEqualTo(MessageType.ASSISTANT);
        }
    }

    @Nested
    @DisplayName("构造校验")
    class Construction {

        @Test
        @DisplayName("redisTemplate 为 null 时抛出 NullPointerException")
        void should_throwNpe_when_redisTemplateIsNull() {
            assertThatThrownBy(() -> new RedisChatMemoryRepository(null, TTL))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("redisTemplate");
        }

        @Test
        @DisplayName("ttl 为 null 时抛出 NullPointerException")
        void should_throwNpe_when_ttlIsNull() {
            assertThatThrownBy(() -> new RedisChatMemoryRepository(redisTemplate, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("ttl");
        }
    }
}
