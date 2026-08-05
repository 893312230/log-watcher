package com.smartops.infrastructure.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SlidingWindowRateLimiter} 单元测试。
 *
 * @author smartops
 * @since 1.0.0
 */
class SlidingWindowRateLimiterTest {

    @Test
    @DisplayName("窗口内未达限时可以获取许可")
    void should_allow_when_underLimit() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(3,
                Duration.ofMinutes(1), Clock.systemUTC());
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.currentCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("窗口内达限后拒绝")
    void should_deny_when_atLimit() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(2,
                Duration.ofMinutes(1), Clock.systemUTC());
        limiter.tryAcquire();
        limiter.tryAcquire();
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    @DisplayName("过期时间戳被清理后可以重新获取")
    void should_allowAfterWindowExpiry() {
        Clock fixed = Clock.fixed(Instant.parse("2026-07-23T10:00:00Z"), ZoneId.of("UTC"));
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(1,
                Duration.ofMinutes(1), fixed);
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();

        // 前进 61 秒
        Clock later = Clock.fixed(Instant.parse("2026-07-23T10:01:01Z"), ZoneId.of("UTC"));
        SlidingWindowRateLimiter limiter2 = new SlidingWindowRateLimiter(1,
                Duration.ofMinutes(1), later);
        assertThat(limiter2.tryAcquire()).isTrue();
    }

    @Test
    @DisplayName("限制为 0 时第一个请求即拒绝")
    void should_denyAlways_when_limitIsZero() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(0,
                Duration.ofMinutes(1), Clock.systemUTC());
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    @DisplayName("currentCount 返回当前窗口内计数")
    void should_reportCurrentCount() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(10,
                Duration.ofMinutes(1), Clock.systemUTC());
        limiter.tryAcquire();
        limiter.tryAcquire();
        assertThat(limiter.currentCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("currentCount 通过 reflection 注入过期条目覆盖清理逻辑")
    void should_cleanExpiredInCurrentCountLoop() throws Exception {
        Clock fixed = Clock.fixed(Instant.parse("2026-07-23T10:00:00Z"), ZoneId.of("UTC"));
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(10,
                Duration.ofMinutes(1), fixed);
        limiter.tryAcquire(); // 1 current

        // 通过 reflection 注入一个过期时间戳（2 分钟前）
        var field = SlidingWindowRateLimiter.class.getDeclaredField("timestamps");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var deque = (java.util.Deque<Instant>) field.get(limiter);
        deque.addFirst(Instant.parse("2026-07-23T09:58:00Z")); // 过期

        // currentCount 应清理过期条目，只保留当前窗口内的 1 条
        assertThat(limiter.currentCount()).isEqualTo(1);
    }
}
