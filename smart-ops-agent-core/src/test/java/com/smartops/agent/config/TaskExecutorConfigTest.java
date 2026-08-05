package com.smartops.agent.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TaskExecutorConfig} 装配测试。
 *
 * @author smartops
 * @since 1.0.0
 */
class TaskExecutorConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TaskExecutorConfig.class);

    @Test
    @DisplayName("max-concurrent 未配置时不激活")
    void should_notActivate_when_propertyMissing() {
        contextRunner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).doesNotHaveBean("agentTaskExecutor");
        });
    }

    @Test
    @DisplayName("max-concurrent 配置后创建线程池")
    void should_createExecutor_when_configured() {
        contextRunner
                .withPropertyValues("smartops.task.max-concurrent=4",
                        "smartops.task.queue-capacity=50")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(ThreadPoolTaskExecutor.class);
                    ThreadPoolTaskExecutor executor = ctx.getBean(
                            "agentTaskExecutor", ThreadPoolTaskExecutor.class);
                    assertThat(executor.getCorePoolSize()).isEqualTo(4);
                });
    }
}
