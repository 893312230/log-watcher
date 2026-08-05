package com.smartops.infrastructure.memory;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 有界会话记忆仓库。
 *
 * <p>基于访问顺序的 LRU 淘汰：当会话数量超过 {@code maxConversations} 时，
 * 自动淘汰最久未访问的会话，防止内存随会话数无界增长（OOM 风险）。
 * 阶段四将替换为 Redis 持久化实现，本类作为阶段一~三的内存实现。</p>
 *
 * <p>线程安全：内部使用 synchronizedMap 保证并发访问安全；
 * 淘汰检查在 LinkedHashMap 锁内完成。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public class BoundedChatMemoryRepository implements ChatMemoryRepository {

    /** 会话 ID → 消息列表，按访问顺序排序的 LRU 结构。 */
    private final Map<String, List<Message>> store;

    /**
     * 构造有界记忆仓库。
     *
     * @param maxConversations 最大会话数，超出时淘汰最久未访问会话，必须为正数
     * @throws IllegalArgumentException 当 maxConversations 非正数时
     */
    public BoundedChatMemoryRepository(int maxConversations) {
        if (maxConversations <= 0) {
            throw new IllegalArgumentException("最大会话数必须为正数: " + maxConversations);
        }
        this.store = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<Message>> eldest) {
                return size() > maxConversations;
            }
        });
    }

    @Override
    public List<String> findConversationIds() {
        synchronized (store) {
            return new ArrayList<>(store.keySet());
        }
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        // LinkedHashMap(accessOrder=true) 的 get 会刷新会话的访问热度
        List<Message> messages = store.get(conversationId);
        return messages != null ? messages : List.of();
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        // 防御性拷贝：MessageWindowChatMemory 会传入可变列表，避免外部修改影响存储
        store.put(conversationId, List.copyOf(messages));
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        store.remove(conversationId);
    }
}
