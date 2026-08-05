package com.smartops.infrastructure.memory.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 中期记忆：Redis 持久化的会话记忆仓库（阶段四，ADR-014）。
 *
 * <p>实现 Spring AI {@link ChatMemoryRepository}，会话消息序列化为 JSON 存入 Redis
 * （key = {@code smartops:memory:{conversationId}}），带 TTL（默认 7 天，
 * {@code smartops.memory.mid-term.ttl-days}），读时刷新 TTL——语义为"会话跨重启
 * 存活、自动过期"。</p>
 *
 * <p>消息以 {type, text, metadata} 三元组存储；TOOL 类型消息无法忠实还原
 * （{@code ToolResponseMessage} 无公开构造器），加载时跳过——本应用
 * ToolCallingAdvisor 配置 conversationHistoryEnabled(false)，工具消息本就不入会话记忆。</p>
 *
 * <p>降级契约：单条会话数据损坏（JSON 无法解析）时视为空会话并记录 warn，
 * 不影响其他会话与本次对话。</p>
 *
 * <p>线程安全：StringRedisTemplate 与 ObjectMapper（只读使用）线程安全。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    private static final Logger log = LoggerFactory.getLogger(RedisChatMemoryRepository.class);

    /** Redis key 前缀。 */
    static final String KEY_PREFIX = "smartops:memory:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造 Redis 会话记忆仓库。
     *
     * @param redisTemplate Redis 客户端
     * @param ttl           会话过期时间（读/写时刷新）
     */
    public RedisChatMemoryRepository(StringRedisTemplate redisTemplate, Duration ttl) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate 不能为 null");
        this.ttl = Objects.requireNonNull(ttl, "ttl 不能为 null");
    }

    /**
     * 列出全部会话 id（去除 key 前缀，字典序）。
     *
     * @return 会话 id 列表
     */
    @Override
    public List<String> findConversationIds() {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        return keys.stream()
                .map(key -> key.substring(KEY_PREFIX.length()))
                .sorted()
                .toList();
    }

    /**
     * 读取会话消息并刷新 TTL。
     *
     * @param conversationId 会话 id
     * @return 消息列表；会话不存在或数据损坏时为空表
     */
    @Override
    public List<Message> findByConversationId(String conversationId) {
        String key = KEY_PREFIX + conversationId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return List.of();
        }
        redisTemplate.expire(key, ttl);
        try {
            List<StoredMessage> stored = objectMapper.readValue(json, new TypeReference<>() {
            });
            return stored.stream()
                    .map(StoredMessage::toMessage)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (JsonProcessingException e) {
            log.warn("会话记忆反序列化失败，视为空会话: conversationId={}, error={}",
                    conversationId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 覆盖式保存会话全部消息并设置 TTL。
     *
     * @param conversationId 会话 id
     * @param messages       消息列表（MessageWindowChatMemory 已按窗口裁剪）
     */
    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        List<StoredMessage> stored = messages.stream().map(StoredMessage::fromMessage).toList();
        try {
            redisTemplate.opsForValue().set(
                    KEY_PREFIX + conversationId, objectMapper.writeValueAsString(stored), ttl);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("会话记忆序列化失败: " + conversationId, e);
        }
    }

    /**
     * 删除会话。
     *
     * @param conversationId 会话 id
     */
    @Override
    public void deleteByConversationId(String conversationId) {
        redisTemplate.delete(KEY_PREFIX + conversationId);
    }

    /**
     * 消息存储三元组。
     *
     * @param type     消息类型（MessageType.name()）
     * @param text     文本内容
     * @param metadata 元数据
     */
    record StoredMessage(String type, String text, Map<String, Object> metadata) {

        /**
         * 从 Message 提取存储形态。
         *
         * @param message 消息
         * @return 存储三元组
         */
        static StoredMessage fromMessage(Message message) {
            return new StoredMessage(message.getMessageType().name(), message.getText(),
                    message.getMetadata());
        }

        /**
         * 还原为 Message；TOOL 类型无法忠实还原（无公开构造器），返回 null 由调用方过滤。
         *
         * @return 还原的消息，TOOL 类型为 null
         */
        Message toMessage() {
            return switch (MessageType.valueOf(type)) {
                case USER -> new UserMessage(text);
                case ASSISTANT -> new AssistantMessage(text);
                case SYSTEM -> new SystemMessage(text);
                case TOOL -> null;
            };
        }
    }
}
