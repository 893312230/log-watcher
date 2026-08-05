package com.smartops.infrastructure.config;

import com.smartops.infrastructure.memory.BoundedChatMemoryRepository;
import com.smartops.infrastructure.memory.impl.RedisChatMemoryRepository;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * 短期/中期记忆配置类。
 *
 * <p>短期记忆（agent.md 阶段一任务5）：基于 Spring AI 的 {@link MessageWindowChatMemory}，
 * 维护固定大小的滑动窗口，超过窗口时按 User/Assistant 轮次对齐淘汰最早的旧消息
 * （Spring AI 2.0 语义）。窗口大小通过 smartops.memory.short-term.window-size 配置，默认 20 条。</p>
 *
 * <p>记忆仓库二选一（ADR-014 记忆分层）：
 * <ul>
 *   <li>{@code smartops.memory.mid-term.enabled=false}（默认）：
 *       {@link BoundedChatMemoryRepository} 有界内存实现，
 *       会话总数上限 smartops.memory.max-conversations（默认 1000），LRU 淘汰</li>
 *   <li>{@code smartops.memory.mid-term.enabled=true}：
 *       {@link RedisChatMemoryRepository} Redis 持久化实现（中期记忆），
 *       会话跨重启存活，TTL smartops.memory.mid-term.ttl-days（默认 7 天，读时刷新）</li>
 * </ul></p>
 *
 * <p>线程安全：MessageWindowChatMemory 内部使用线程安全存储，Bean 单例可被多线程共享。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Configuration
public class ChatMemoryConfig {

    /**
     * 短期记忆窗口大小。
     * 从 application.yml 读取，默认 20。对应 agent.md 第六章 6.2 节配置项规范。
     */
    @Value("${smartops.memory.short-term.window-size:20}")
    private int windowSize = 20;

    /**
     * 最大会话数上限，超出后按 LRU 淘汰最久未访问的会话。
     * 防止长期运行时记忆 Map 无界增长导致 OOM。
     */
    @Value("${smartops.memory.max-conversations:1000}")
    private int maxConversations = 1000;

    /**
     * 默认记忆仓库：有界内存实现（mid-term 关闭时）。
     *
     * @return BoundedChatMemoryRepository 实例
     */
    @Bean
    @ConditionalOnProperty(name = "smartops.memory.mid-term.enabled",
            havingValue = "false", matchIfMissing = true)
    public ChatMemoryRepository inMemoryChatMemoryRepository() {
        return new BoundedChatMemoryRepository(maxConversations);
    }

    /**
     * 中期记忆仓库：Redis 持久化实现（mid-term 开启时）。
     *
     * @param redisTemplate Redis 客户端
     * @param ttlDays       会话 TTL 天数（smartops.memory.mid-term.ttl-days，默认 7）
     * @return RedisChatMemoryRepository 实例
     */
    @Bean
    @ConditionalOnProperty(name = "smartops.memory.mid-term.enabled", havingValue = "true")
    public ChatMemoryRepository redisChatMemoryRepository(
            StringRedisTemplate redisTemplate,
            @Value("${smartops.memory.mid-term.ttl-days:7}") long ttlDays) {
        return new RedisChatMemoryRepository(redisTemplate, Duration.ofDays(ttlDays));
    }

    /**
     * 构建短期记忆 Bean。
     *
     * @param chatMemoryRepository 记忆仓库（内存或 Redis 实现，由 mid-term 开关决定）
     * @return 配置好窗口大小的 MessageWindowChatMemory 实例
     */
    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(windowSize)
                .build();
    }
}
