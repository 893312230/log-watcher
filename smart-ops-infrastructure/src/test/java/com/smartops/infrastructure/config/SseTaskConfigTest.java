package com.smartops.infrastructure.config;

import com.smartops.infrastructure.sse.SseTaskRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SseTaskConfig} 装配测试。
 *
 * @author smartops
 * @since 1.0.0
 */
class SseTaskConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SseTaskConfig.class);

    @Test
    @DisplayName("使用默认值创建 SseTaskRegistry Bean")
    void should_createRegistryWithDefaults() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(SseTaskRegistry.class);
            assertThat(ctx.getBean(SseTaskRegistry.class).size()).isZero();
        });
    }

    @Test
    @DisplayName("自定义配置值透传")
    void should_useCustomConfig() {
        runner.withPropertyValues(
                "smartops.sse.task.max-conversations=50",
                "smartops.sse.task.completed-ttl-minutes=3",
                "smartops.sse.task.running-max-minutes=8")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    SseTaskRegistry reg = ctx.getBean(SseTaskRegistry.class);
                    assertThat(reg).isNotNull();
                });
    }
}
