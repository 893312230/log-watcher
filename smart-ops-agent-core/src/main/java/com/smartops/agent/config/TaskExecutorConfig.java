package com.smartops.agent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 任务执行线程池配置（阶段五韧性增强）。
 *
 * <p>AgentRouter 可选提交到有界线程池，队列满时按 CallerRunsPolicy 降级为
 * 调用线程同步执行（不丢弃请求）；通过 {@code smartops.task.max-concurrent}
 * 控制最大并发，0 或未配置时不创建 Bean，AgentRouter 同步执行。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(name = "smartops.task.max-concurrent")
public class TaskExecutorConfig {

    /** 核心/最大线程数。 */
    @Value("${smartops.task.max-concurrent}")
    private int maxConcurrent;

    /** 队列容量（满后 CallerRunsPolicy）。 */
    @Value("${smartops.task.queue-capacity:100}")
    private int queueCapacity;

    /**
     * Agent 任务执行线程池 Bean。
     *
     * @return 有界线程池（core=max, queue bounded, CallerRunsPolicy）
     */
    @Bean("agentTaskExecutor")
    public ThreadPoolTaskExecutor agentTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(maxConcurrent);
        executor.setMaxPoolSize(maxConcurrent);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("agent-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
