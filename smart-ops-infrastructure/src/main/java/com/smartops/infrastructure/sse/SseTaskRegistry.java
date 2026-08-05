package com.smartops.infrastructure.sse;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 任务注册表（阶段五断线重连）。
 *
 * <p>按 conversationId 管理 {@link SseTask} 生命周期。线程安全：
 * {@link ConcurrentHashMap} 保证并发读写；淘汰同步在写入时执行。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public class SseTaskRegistry {

    private final ConcurrentHashMap<String, SseTask> tasks;
    private final int maxConversations;
    private final Duration completedTtl;
    private final Duration runningMaxAge;

    /**
     * 构造注册表。
     *
     * @param maxConversations 最大缓存会话数
     * @param completedTtl     COMPLETED 任务保留时长
     * @param runningMaxAge    RUNNING 任务最大存活时长（僵尸保护）
     */
    public SseTaskRegistry(int maxConversations, Duration completedTtl, Duration runningMaxAge) {
        this.tasks = new ConcurrentHashMap<>();
        this.maxConversations = maxConversations;
        this.completedTtl = completedTtl;
        this.runningMaxAge = runningMaxAge;
    }

    /**
     * 获取或创建 SseTask。
     *
     * @param conversationId 会话 ID
     * @param taskFactory    任务工厂（仅在首次创建时调用）
     * @return 已存在或新创建的 SseTask
     */
    public SseTask getOrCreate(String conversationId,
                                java.util.function.Supplier<SseTask> taskFactory) {
        return tasks.compute(conversationId, (key, existing) -> {
            if (existing != null && !isExpired(existing)) {
                return existing;
            }
            SseTask task = taskFactory.get();
            evictIfNeeded();
            return task;
        });
    }

    /**
     * 按 conversationId 查找 SseTask。
     *
     * @param conversationId 会话 ID
     * @return 命中的 SseTask，不存在或已过期返回 null
     */
    public SseTask get(String conversationId) {
        SseTask task = tasks.get(conversationId);
        if (task == null || isExpired(task)) {
            if (task != null) {
                tasks.remove(conversationId, task);
            }
            return null;
        }
        return task;
    }

    /**
     * 当前缓存的会话数。
     */
    public int size() {
        return tasks.size();
    }

    /**
     * 判断任务是否已过期。
     */
    private boolean isExpired(SseTask task) {
        if (task.isCompleted() && task.completedAt() != null) {
            return task.completedAt().plus(completedTtl).isBefore(Instant.now());
        }
        if (!task.isCompleted() && task.startedAt() != null) {
            return task.startedAt().plus(runningMaxAge).isBefore(Instant.now());
        }
        return false;
    }

    /**
     * 超过容量上限时驱逐一个任务（优先 COMPLETED，其次任意）。
     */
    private synchronized void evictIfNeeded() {
        if (tasks.size() <= maxConversations) {
            return;
        }
        // 优先驱逐 COMPLETED
        for (var entry : tasks.entrySet()) {
            if (entry.getValue().isCompleted()) {
                tasks.remove(entry.getKey());
                return;
            }
        }
        // 无 COMPLETED → 驱逐任意一个
        tasks.remove(tasks.keys().nextElement());
    }
}
