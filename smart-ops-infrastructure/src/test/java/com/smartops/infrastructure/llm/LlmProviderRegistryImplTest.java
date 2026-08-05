package com.smartops.infrastructure.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link LlmProviderRegistryImpl} 单元测试。
 *
 * <p>覆盖：注册、按名称获取、默认 Provider、按工具能力选择、
 * 空列表校验、默认名称缺失校验。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class LlmProviderRegistryImplTest {

    private LlmProvider provider(String name, boolean supportsTools) {
        return new OpenAiLlmProvider(name, "http://localhost:1234/v1", "sk-test", "model",
                0.3, supportsTools, 0.0, 2.0);
    }

    @Test
    @DisplayName("按名称获取命中")
    void should_findByName_when_registered() {
        LlmProviderRegistry registry = new LlmProviderRegistryImpl(
                List.of(provider("deepseek", true), provider("kimi", false)),
                "deepseek");

        assertThat(registry.getByName("deepseek")).isPresent();
        assertThat(registry.getByName("deepseek").get().name()).isEqualTo("deepseek");
        assertThat(registry.getByName("kimi")).isPresent();
        assertThat(registry.getByName("nonexistent")).isEmpty();
    }

    @Test
    @DisplayName("getDefault 返回配置的默认 Provider")
    void should_returnDefault_when_configured() {
        LlmProviderRegistry registry = new LlmProviderRegistryImpl(
                List.of(provider("ds", true), provider("km", false)),
                "km");

        assertThat(registry.getDefault().name()).isEqualTo("km");
    }

    @Test
    @DisplayName("getToolCapable 返回首个 supportsTools=true 的 Provider")
    void should_returnFirstToolCapable_when_exists() {
        LlmProviderRegistry registry = new LlmProviderRegistryImpl(
                List.of(provider("no-tool", false), provider("has-tool", true)),
                "no-tool");

        assertThat(registry.getToolCapable()).isPresent();
        assertThat(registry.getToolCapable().get().name()).isEqualTo("has-tool");
    }

    @Test
    @DisplayName("无 supportsTools=true 的 Provider 时 getToolCapable 返回 empty")
    void should_returnEmptyToolCapable_when_noneSupportTools() {
        LlmProviderRegistry registry = new LlmProviderRegistryImpl(
                List.of(provider("a", false), provider("b", false)),
                "a");

        assertThat(registry.getToolCapable()).isEmpty();
    }

    @Test
    @DisplayName("all 返回所有已注册 Provider")
    void should_returnAll_when_queried() {
        LlmProviderRegistry registry = new LlmProviderRegistryImpl(
                List.of(provider("a", true), provider("b", false)),
                "a");

        assertThat(registry.all()).hasSize(2);
    }

    @Test
    @DisplayName("空 Provider 列表抛 IllegalArgumentException")
    void should_throw_when_emptyList() {
        assertThatThrownBy(() -> new LlmProviderRegistryImpl(List.of(), "any"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少");
    }

    @Test
    @DisplayName("默认名称不存在抛 IllegalArgumentException")
    void should_throw_when_defaultNameNotFound() {
        List<LlmProvider> providers = List.of(provider("a", true));
        assertThatThrownBy(() -> new LlmProviderRegistryImpl(providers, "b"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("默认 Provider");
    }
}
