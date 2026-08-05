package com.smartops.infrastructure.config;

import com.smartops.infrastructure.llm.LlmProvider;
import com.smartops.infrastructure.llm.LlmProviderRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ModelProviderConfig} 装配测试（ApplicationContextRunner）。
 *
 * <p>覆盖：单/多 Provider 装配、默认 Provider 指定、必填字段校验失败、
 * 未配置 default-provider 时不激活。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class ModelProviderConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ModelProviderConfig.class);

    @Test
    @DisplayName("配置多个 Provider 时装配 Registry 并对齐能力标志")
    void should_assembleRegistry_when_multipleProviders() {
        contextRunner
                .withPropertyValues(
                        "smartops.llm.default-provider=deepseek",
                        "smartops.llm.providers.deepseek.type=openai",
                        "smartops.llm.providers.deepseek.base-url=http://localhost:1/v1",
                        "smartops.llm.providers.deepseek.api-key=sk-ds",
                        "smartops.llm.providers.deepseek.model=deepseek-chat",
                        "smartops.llm.providers.deepseek.temperature=0.3",
                        "smartops.llm.providers.deepseek.supports-tools=true",
                        "smartops.llm.providers.kimi.type=openai",
                        "smartops.llm.providers.kimi.base-url=http://localhost:2/v1",
                        "smartops.llm.providers.kimi.api-key=sk-ki",
                        "smartops.llm.providers.kimi.model=kimi-for-coding",
                        "smartops.llm.providers.kimi.temperature=1.0",
                        "smartops.llm.providers.kimi.supports-tools=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(LlmProviderRegistry.class);
                    LlmProviderRegistry registry = context.getBean(LlmProviderRegistry.class);
                    assertThat(registry.all()).hasSize(2);
                    assertThat(registry.getDefault().name()).isEqualTo("deepseek");
                    assertThat(registry.getToolCapable()).isPresent();
                    assertThat(registry.getToolCapable().get().name()).isEqualTo("deepseek");
                });
    }

    @Test
    @DisplayName("base-url 为空时启动失败")
    void should_fail_when_baseUrlMissing() {
        contextRunner
                .withPropertyValues(
                        "smartops.llm.default-provider=p1",
                        "smartops.llm.providers.p1.type=openai",
                        "smartops.llm.providers.p1.api-key=sk",
                        "smartops.llm.providers.p1.model=m1",
                        "smartops.llm.providers.p1.temperature=0.3",
                        "smartops.llm.providers.p1.supports-tools=false")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()))
                            .hasMessageContaining("base-url");
                });
    }

    @Test
    @DisplayName("api-key 为空时启动失败")
    void should_fail_when_apiKeyMissing() {
        contextRunner
                .withPropertyValues(
                        "smartops.llm.default-provider=p1",
                        "smartops.llm.providers.p1.type=openai",
                        "smartops.llm.providers.p1.base-url=http://localhost/v1",
                        "smartops.llm.providers.p1.model=m1",
                        "smartops.llm.providers.p1.temperature=0.3",
                        "smartops.llm.providers.p1.supports-tools=false")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()))
                            .hasMessageContaining("api-key");
                });
    }

    @Test
    @DisplayName("不支持的类型启动失败")
    void should_fail_when_unsupportedType() {
        contextRunner
                .withPropertyValues(
                        "smartops.llm.default-provider=p1",
                        "smartops.llm.providers.p1.type=anthropic",
                        "smartops.llm.providers.p1.base-url=http://localhost/v1",
                        "smartops.llm.providers.p1.api-key=sk",
                        "smartops.llm.providers.p1.model=m1",
                        "smartops.llm.providers.p1.temperature=0.3",
                        "smartops.llm.providers.p1.supports-tools=false")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()))
                            .hasMessageContaining("不支持");
                });
    }

    @Test
    @DisplayName("type 未设置时默认为 openai 正常装配")
    void should_defaultToOpenai_when_typeNotSet() {
        contextRunner
                .withPropertyValues(
                        "smartops.llm.default-provider=p1",
                        "smartops.llm.providers.p1.base-url=http://localhost/v1",
                        "smartops.llm.providers.p1.api-key=sk",
                        "smartops.llm.providers.p1.model=m1",
                        "smartops.llm.providers.p1.temperature=0.3",
                        "smartops.llm.providers.p1.supports-tools=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(LlmProviderRegistry.class);
                });
    }

    @Test
    @DisplayName("未配 default-provider 时不激活（无 Registry Bean）")
    void should_notActivate_when_noDefaultProvider() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(LlmProviderRegistry.class);
        });
    }

    @Test
    @DisplayName("单个 Provider 装配成功")
    void should_assembleSingleProvider_when_validConfig() {
        contextRunner
                .withPropertyValues(
                        "smartops.llm.default-provider=solo",
                        "smartops.llm.providers.solo.type=openai",
                        "smartops.llm.providers.solo.base-url=http://localhost/v1",
                        "smartops.llm.providers.solo.api-key=sk",
                        "smartops.llm.providers.solo.model=m1",
                        "smartops.llm.providers.solo.temperature=0.3",
                        "smartops.llm.providers.solo.supports-tools=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    LlmProviderRegistry reg = context.getBean(LlmProviderRegistry.class);
                    assertThat(reg.all()).hasSize(1);
                    LlmProvider p = reg.getDefault();
                    assertThat(p.name()).isEqualTo("solo");
                    assertThat(p.supportsTools()).isTrue();
                });
    }

    @Test
    @DisplayName("自定义温度上下限正确透传")
    void should_useCustomTemperatureBounds_when_configured() {
        contextRunner
                .withPropertyValues(
                        "smartops.llm.default-provider=p1",
                        "smartops.llm.providers.p1.type=openai",
                        "smartops.llm.providers.p1.base-url=http://localhost/v1",
                        "smartops.llm.providers.p1.api-key=sk",
                        "smartops.llm.providers.p1.model=m1",
                        "smartops.llm.providers.p1.temperature=0.8",
                        "smartops.llm.providers.p1.supports-tools=true",
                        "smartops.llm.providers.p1.min-temperature=0.5",
                        "smartops.llm.providers.p1.max-temperature=1.5")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    LlmProvider p = context.getBean(LlmProviderRegistry.class).getDefault();
                    assertThat(p.minTemperature()).isEqualTo(0.5);
                    assertThat(p.maxTemperature()).isEqualTo(1.5);
                });
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
