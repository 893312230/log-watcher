package com.smartops.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Runbook 异步执行线程池配置（阶段十三 WS4）。
 *
 * <p>execute 接口落库 RUNNING 后立即返回，实际执行提交到本线程池；
 * 队列满时按 CallerRunsPolicy 降级为调用线程同步执行（不丢弃任务）。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Configuration
public class RunbookTaskExecutorConfig {

    /**
     * Runbook 执行线程池 Bean。
     *
     * @return 有界线程池（core=2, max=4, queue=50, CallerRunsPolicy）
     */
    @Bean("runbookTaskExecutor")
    public Executor runbookTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("runbook-exec-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
