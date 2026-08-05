package com.smartops.agent.logwatch.config;

import com.smartops.agent.logwatch.AlertPipelineService;
import com.smartops.agent.logwatch.LogKeywordMatcher;
import com.smartops.agent.logwatch.pipeline.impl.L0SuppressionLayer;
import com.smartops.agent.logwatch.pipeline.impl.L1ClassifyLayer;
import com.smartops.agent.logwatch.pipeline.impl.L2RagLayer;
import com.smartops.agent.logwatch.pipeline.impl.L3LlmLayer;
import com.smartops.agent.logwatch.pipeline.impl.L4SupervisorLayer;
import com.smartops.agent.logwatch.pipeline.impl.MlClassifyLayer;
import com.smartops.agent.orchestrator.SupervisorAgent;
import com.smartops.common.exception.LogWatchException;
import com.smartops.domain.logwatch.port.AlertRepository;
import com.smartops.domain.logwatch.port.LogLevelClassifier;
import com.smartops.infrastructure.chat.ChatService;
import com.smartops.infrastructure.logwatch.impl.SseAlertNotifier;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link LogWatchConfig} 装配测试（ApplicationContextRunner）。
 *
 * <p>覆盖：enabled 开启时五层管线与采集源装配、关键字合并、
 * 缺省关闭时无 Bean、未知采集源类型启动失败。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class LogWatchConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(StubBeans.class, LogWatchConfig.class);

    @Test
    @DisplayName("enabled=true 时装配关键字匹配器、六层分析层、管线与生命周期托管器")
    void should_assemblePipeline_when_enabled() {
        contextRunner
                .withPropertyValues(
                        "smartops.logwatch.enabled=true",
                        "smartops.logwatch.sources[0].type=file",
                        "smartops.logwatch.sources[0].path=/tmp/app.log",
                        "smartops.logwatch.sources[0].keywords[0]=OutOfMemoryError",
                        "smartops.logwatch.sources[1].type=jar",
                        "smartops.logwatch.sources[1].path=/opt/app.jar",
                        "smartops.logwatch.sources[1].keywords[0]=连接超时",
                        "smartops.logwatch.exclude-keywords[0]=c.s.agent.orchestrator")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(LogKeywordMatcher.class);
                    assertThat(context).hasSingleBean(L0SuppressionLayer.class);
                    assertThat(context).hasSingleBean(L1ClassifyLayer.class);
                    assertThat(context).hasSingleBean(MlClassifyLayer.class);
                    assertThat(context).hasSingleBean(L2RagLayer.class);
                    assertThat(context).hasSingleBean(L3LlmLayer.class);
                    assertThat(context).hasSingleBean(L4SupervisorLayer.class);
                    assertThat(context).hasSingleBean(AlertPipelineService.class);
                    assertThat(context).hasSingleBean(LogWatchRunner.class);

                    LogKeywordMatcher matcher = context.getBean(LogKeywordMatcher.class);
                    assertThat(matcher.match("java.lang.OutOfMemoryError: heap")).isPresent();
                    assertThat(matcher.match("上游连接超时重试")).isPresent();
                    assertThat(matcher.match("普通 INFO 日志")).isEmpty();
                    assertThat(matcher.match("c.s.agent.orchestrator.SupervisorAgent : 告警级别：ERROR"))
                            .isEmpty();
                });
    }

    @Test
    @DisplayName("ml.enabled=true 时装配 Tribuo 分类器并完成启动训练（留出集达标）")
    void should_assembleClassifier_when_mlEnabled() {
        contextRunner
                .withPropertyValues(
                        "smartops.logwatch.enabled=true",
                        "smartops.logwatch.ml.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(LogLevelClassifier.class);
                    LogLevelClassifier classifier = context.getBean(LogLevelClassifier.class);
                    assertThat(classifier.isReady()).isTrue();
                    assertThat(classifier.classify("Connection refused by db-01").level())
                            .isEqualTo(com.smartops.common.enums.AlertLevel.ERROR);
                });
    }

    @Test
    @DisplayName("ml.enabled 缺省时不创建分类器 Bean，ML 层仍装配（内部恒抑制）")
    void should_notCreateClassifier_when_mlDisabled() {
        contextRunner
                .withPropertyValues("smartops.logwatch.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(LogLevelClassifier.class);
                    assertThat(context).hasSingleBean(MlClassifyLayer.class);
                });
    }

    @Test
    @DisplayName("缺省配置使用安全默认值并可装配空采集源管线")
    void should_useDefaults_when_noOverrides() {
        contextRunner
                .withPropertyValues("smartops.logwatch.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    LogWatchProperties properties = context.getBean(LogWatchProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getSources()).isEmpty();
                    assertThat(properties.getPollIntervalMs()).isEqualTo(500);
                    assertThat(properties.getStateDir()).isEqualTo("./logwatch-state");
                    assertThat(properties.getQueueCapacity()).isEqualTo(2000);
                    assertThat(properties.getL0().getWindowSeconds()).isEqualTo(300);
                    assertThat(properties.getMl().isEnabled()).isFalse();
                    assertThat(properties.getMl().getConfidenceThreshold()).isEqualTo(0.85);
                    assertThat(properties.getMl().getMinAccuracy()).isEqualTo(0.8);
                    assertThat(properties.getMl().getTrainingDataPath()).isNull();
                    assertThat(properties.getMl().getSeed()).isEqualTo(42);
                    assertThat(properties.getL3().getRatePerMinute()).isEqualTo(10);
                    assertThat(properties.getL3().getEscalateOccurrenceThreshold()).isEqualTo(3);
                    assertThat(properties.getL4().getDailyLimit()).isEqualTo(20);
                    assertThat(properties.getSse().getBufferSize()).isEqualTo(256);
                });
    }

    @Test
    @DisplayName("未开启时不创建任何 logwatch Bean")
    void should_createNothing_when_disabled() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(AlertPipelineService.class);
            assertThat(context).doesNotHaveBean(LogWatchRunner.class);
        });
    }

    @Test
    @DisplayName("未知采集源类型启动失败并抛出 LogWatchException")
    void should_failFast_when_sourceTypeUnknown() {
        contextRunner
                .withPropertyValues(
                        "smartops.logwatch.enabled=true",
                        "smartops.logwatch.sources[0].type=unknown",
                        "smartops.logwatch.sources[0].path=/tmp/x.log")
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable failure = context.getStartupFailure();
                    assertThat(rootCause(failure)).isInstanceOf(LogWatchException.class);
                });
    }

    @Test
    @DisplayName("enabled=true 时装配队列丢弃与 ML 救援计数两个 MeterBinder 并可导出 Gauge")
    void should_registerQueueGauge_when_enabled() {
        contextRunner
                .withPropertyValues("smartops.logwatch.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(MeterBinder.class)).hasSize(2);

                    SimpleMeterRegistry registry = new SimpleMeterRegistry();
                    context.getBeansOfType(MeterBinder.class).values()
                            .forEach(binder -> binder.bindTo(registry));
                    assertThat(registry.get("smartops.logwatch.queue.dropped")
                            .gauge().value()).isZero();
                    assertThat(registry.get("smartops.logwatch.ml.rescued")
                            .gauge().value()).isZero();
                });
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /**
     * 装配桩：LLM/Supervisor/持久化以 Mockito 替身注入，通知器用真实实例。
     */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties
    static class StubBeans {

        @Bean
        ChatService chatService() {
            return mock(ChatService.class);
        }

        @Bean
        SupervisorAgent supervisorAgent() {
            return mock(SupervisorAgent.class);
        }

        @Bean
        AlertRepository alertRepository() {
            return mock(AlertRepository.class);
        }

        @Bean
        SseAlertNotifier sseAlertNotifier() {
            return new SseAlertNotifier(8);
        }
    }
}
