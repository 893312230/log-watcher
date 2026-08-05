package com.smartops.infrastructure.config;

import com.smartops.infrastructure.sse.SseTaskRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * SSE 断线重连配置（阶段五 P12）。
 *
 * @author smartops
 * @since 1.0.0
 */
@Configuration
public class SseTaskConfig {

    @Value("${smartops.sse.task.max-conversations:200}")
    private int maxConversations;

    @Value("${smartops.sse.task.completed-ttl-minutes:5}")
    private int completedTtlMinutes;

    @Value("${smartops.sse.task.running-max-minutes:10}")
    private int runningMaxMinutes;

    /**
     * SSE 任务注册表 Bean。
     *
     * @return 注册表实例
     */
    @Bean
    public SseTaskRegistry sseTaskRegistry() {
        return new SseTaskRegistry(maxConversations,
                Duration.ofMinutes(completedTtlMinutes),
                Duration.ofMinutes(runningMaxMinutes));
    }
}
