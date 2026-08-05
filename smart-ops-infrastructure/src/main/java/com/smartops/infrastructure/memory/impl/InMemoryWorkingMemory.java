package com.smartops.infrastructure.memory.impl;

import com.smartops.infrastructure.memory.WorkingMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 工作记忆进程内实现：两级有界 LRU（ADR-014 决策3）。
 *
 * <p>外层按 conversationId 隔离，会话总数超过 maxConversations 时淘汰最久未访问的会话；
 * 内层为单会话条目表，条目数超过 maxEntries 时淘汰最久未访问的条目。
 * 读/写均刷新对应层级热度（access-order LinkedHashMap）。</p>
 *
 * <p>进程内实现，不持久化，重启即丢——工作记忆语义为"单次任务执行过程"，
 * 跨重启存活由中期记忆（RedisChatMemoryRepository）承担。</p>
 *
 * <p>线程安全：内部使用 synchronizedMap，单条 put/get/clear 原子；
 * 组合操作（读-改-写）不保证原子性，执行器按会话串行使用无竞态。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(name = "smartops.memory.working.enabled",
        havingValue = "true", matchIfMissing = true)
public class InMemoryWorkingMemory implements WorkingMemory {

    private final int maxEntries;
    private final Map<String, Map<String, String>> conversations;

    /**
     * 构造有界工作记忆。
     *
     * @param maxEntries       单会话条目数上限（smartops.memory.working.max-entries，默认 100）
     * @param maxConversations 会话总数上限（smartops.memory.working.max-conversations，默认 500）
     * @throws IllegalArgumentException 任一上限非正数时
     */
    public InMemoryWorkingMemory(
            @Value("${smartops.memory.working.max-entries:100}") int maxEntries,
            @Value("${smartops.memory.working.max-conversations:500}") int maxConversations) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries 必须为正数: " + maxEntries);
        }
        if (maxConversations <= 0) {
            throw new IllegalArgumentException("maxConversations 必须为正数: " + maxConversations);
        }
        this.maxEntries = maxEntries;
        this.conversations = Collections.synchronizedMap(
                new LinkedHashMap<>(16, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, Map<String, String>> eldest) {
                        return size() > maxConversations;
                    }
                });
    }

    /**
     * {@inheritDoc}
     *
     * <p>同 key 覆盖会刷新条目热度；写入新条目可能触发条目级与会话级淘汰。</p>
     */
    @Override
    public void put(String conversationId, String key, String value) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId 不能为 null 或空白");
        }
        if (key == null) {
            throw new IllegalArgumentException("key 不能为 null");
        }
        if (value == null) {
            throw new IllegalArgumentException("value 不能为 null");
        }
        Map<String, String> entries = conversations.computeIfAbsent(conversationId,
                id -> newBoundedEntryMap());
        synchronized (entries) {
            entries.put(key, value);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>命中时刷新条目与会话两级热度。</p>
     */
    @Override
    public Optional<String> get(String conversationId, String key) {
        Map<String, String> entries = conversations.get(conversationId);
        if (entries == null) {
            return Optional.empty();
        }
        synchronized (entries) {
            return Optional.ofNullable(entries.get(key));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear(String conversationId) {
        conversations.remove(conversationId);
    }

    /**
     * 构建单会话的有界条目表（access-order LRU，超过 maxEntries 淘汰最久未访问条目）。
     *
     * @return 有界条目表
     */
    private Map<String, String> newBoundedEntryMap() {
        return new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > maxEntries;
            }
        };
    }
}
