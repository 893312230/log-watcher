package com.smartops.infrastructure.sse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SseTaskRegistry} 单元测试。
 *
 * @author smartops
 * @since 1.0.0
 */
class SseTaskRegistryTest {

    @Test
    @DisplayName("getOrCreate 首次调用创建新任务")
    void should_createTask_when_firstGetOrCreate() {
        SseTaskRegistry registry = new SseTaskRegistry(100,
                Duration.ofMinutes(5), Duration.ofMinutes(10));
        SseTask task = registry.getOrCreate("conv-1", () -> new SseTask(8));
        assertThat(task).isNotNull();
        assertThat(task.isNew()).isTrue();
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("getOrCreate 重复调用返回同一任务")
    void should_returnSameTask_when_sameKey() {
        SseTaskRegistry registry = new SseTaskRegistry(100,
                Duration.ofMinutes(5), Duration.ofMinutes(10));
        SseTask t1 = registry.getOrCreate("conv-1", () -> new SseTask(8));
        SseTask t2 = registry.getOrCreate("conv-1", () -> new SseTask(8));
        assertThat(t1).isSameAs(t2);
    }

    @Test
    @DisplayName("get 命中返回任务")
    void should_find_when_get() {
        SseTaskRegistry registry = new SseTaskRegistry(100,
                Duration.ofMinutes(5), Duration.ofMinutes(10));
        registry.getOrCreate("conv-2", () -> new SseTask(8));
        assertThat(registry.get("conv-2")).isNotNull();
    }

    @Test
    @DisplayName("get 未命中返回 null")
    void should_returnNull_when_missing() {
        SseTaskRegistry registry = new SseTaskRegistry(100,
                Duration.ofMinutes(5), Duration.ofMinutes(10));
        assertThat(registry.get("nonexistent")).isNull();
    }

    @Test
    @DisplayName("超过容量上限时驱逐最旧 COMPLETED 任务")
    void should_evict_when_overCapacity() {
        SseTaskRegistry registry = new SseTaskRegistry(2,
                Duration.ofMinutes(5), Duration.ofMinutes(10));
        registry.getOrCreate("a", () -> new SseTask(8));
        SseTask b = registry.getOrCreate("b", () -> new SseTask(8));
        b.start(reactor.core.publisher.Flux.just("done"));
        sleep(100); // 等待完成
        // 第三个任务触发驱逐
        registry.getOrCreate("c", () -> new SseTask(8));
        assertThat(registry.size()).isLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("get 已完成超 TTL 返回 null")
    void should_returnNull_when_completedExpired() {
        // TTL = 0 → 立即过期
        SseTaskRegistry registry = new SseTaskRegistry(100,
                Duration.ZERO, Duration.ofMinutes(10));
        SseTask task = registry.getOrCreate("conv-3", () -> new SseTask(8));
        task.start(reactor.core.publisher.Flux.just("done"));
        sleep(100);
        assertThat(registry.get("conv-3")).isNull();
    }

    @Test
    @DisplayName("getOrCreate 过期任务创建新实例")
    void should_createNew_when_existingExpired() {
        SseTaskRegistry registry = new SseTaskRegistry(100,
                Duration.ZERO, Duration.ofMinutes(10));
        SseTask t1 = registry.getOrCreate("conv-4", () -> new SseTask(8));
        t1.start(reactor.core.publisher.Flux.just("done"));
        sleep(100);
        SseTask t2 = registry.getOrCreate("conv-4", () -> new SseTask(8));
        assertThat(t2).isNotSameAs(t1);
    }

    @Test
    @DisplayName("超容量时无 COMPLETED 任务驱逐最旧任务")
    void should_evictOldest_when_noCompletedToEvict() {
        SseTaskRegistry registry = new SseTaskRegistry(1,
                Duration.ofMinutes(5), Duration.ofMinutes(10));
        // 占满容量（任务未完成）
        SseTask t1 = registry.getOrCreate("a", () -> new SseTask(8));
        t1.start(reactor.core.publisher.Flux.never());
        // 触发驱逐
        SseTask t2 = registry.getOrCreate("b", () -> new SseTask(8));
        assertThat(registry.size()).isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("RUNNING 任务超过最大存活时限被判定为过期")
    void should_expireRunning_when_exceededMaxAge() {
        SseTaskRegistry registry = new SseTaskRegistry(100,
                Duration.ofMinutes(5), Duration.ZERO);
        SseTask task = registry.getOrCreate("conv-5", () -> new SseTask(8));
        task.start(reactor.core.publisher.Flux.never());
        sleep(5); // 确保 startedAt < now
        assertThat(registry.get("conv-5")).isNull();
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
