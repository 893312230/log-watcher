package com.smartops.infrastructure.llm;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 滑动窗口限流器（阶段五韧性增强）。
 *
 * <p>复用 L3LlmLayer 的 {@link ArrayDeque} 模式，窗口内调用数达上限时拒绝，
 * 否则登记。线程安全：{@link #tryAcquire()} 以 {@code synchronized} 保护。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public class SlidingWindowRateLimiter {

    private final int maxPerWindow;
    private final Duration window;
    private final Deque<Instant> timestamps;
    private final Clock clock;

    /**
     * 构造限流器。
     *
     * @param maxPerWindow 窗口内最大调用次数
     * @param window       滑动窗口持续时间
     * @param clock        时钟（测试可注入固定时钟）
     */
    public SlidingWindowRateLimiter(int maxPerWindow, Duration window, Clock clock) {
        this.maxPerWindow = maxPerWindow;
        this.window = window;
        this.timestamps = new ArrayDeque<>(maxPerWindow);
        this.clock = clock;
    }

    /**
     * 尝试获取许可。
     *
     * @return true 获取成功，false 当前窗口已满
     */
    public synchronized boolean tryAcquire() {
        Instant now = clock.instant();
        Instant threshold = now.minus(window);
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(threshold)) {
            timestamps.pollFirst();
        }
        if (timestamps.size() >= maxPerWindow) {
            return false;
        }
        timestamps.addLast(now);
        return true;
    }

    /**
     * 当前窗口内已使用的许可数。
     *
     * @return 窗口内调用计数
     */
    public synchronized int currentCount() {
        Instant threshold = clock.instant().minus(window);
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(threshold)) {
            timestamps.pollFirst();
        }
        return timestamps.size();
    }
}
