package com.smartops.infrastructure.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link OpenAiLlmProvider} 单元测试。
 *
 * <p>覆盖：元数据透传、ChatClient 构建、温度边界。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class OpenAiLlmProviderTest {

    private final OpenAiLlmProvider provider = new OpenAiLlmProvider(
            "deepseek", "http://localhost:1234/v1", "sk-test", "test-model",
            0.5, true, 0.0, 2.0);

    @Test
    @DisplayName("元数据从构造参数透传")
    void should_exposeMetadata_when_constructed() {
        assertThat(provider.name()).isEqualTo("deepseek");
        assertThat(provider.type()).isEqualTo("openai");
        assertThat(provider.supportsTools()).isTrue();
        assertThat(provider.minTemperature()).isEqualTo(0.0);
        assertThat(provider.maxTemperature()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("ChatClient 非 null 且每次返回同一实例")
    void should_buildChatClient_when_constructed() {
        assertThat(provider.chatClient()).isNotNull();
        assertThat(provider.chatClient()).isSameAs(provider.chatClient());
    }

    @Test
    @DisplayName("supportsTools=false 时正确标识")
    void should_identifyNoTools_when_supportsToolsFalse() {
        OpenAiLlmProvider noTool = new OpenAiLlmProvider(
                "kimi", "http://localhost:1234/v1", "sk-test", "model",
                1.0, false, 0.5, 1.5);
        assertThat(noTool.supportsTools()).isFalse();
    }

    @Test
    @DisplayName("温度上下限独立于默认 temperature")
    void should_haveSeparateBounds_when_temperatureDiffers() {
        assertThat(provider.minTemperature()).isEqualTo(0.0);
        assertThat(provider.maxTemperature()).isEqualTo(2.0);
    }
}
