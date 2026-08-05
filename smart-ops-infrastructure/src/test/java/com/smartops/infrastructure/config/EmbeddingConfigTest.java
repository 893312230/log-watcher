package com.smartops.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EmbeddingConfig} 单元测试。
 *
 * <p>验证：Bean 构造时端点/密钥/模型/维度正确传入 options；
 * {@code smartops.embedding.enabled} 开关两分支的 Bean 创建行为。
 * 全程不发起网络请求（OpenAiEmbeddingModel 构造为惰性连接）。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class EmbeddingConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EmbeddingConfig.class));

    /**
     * 构造注入好配置值的 EmbeddingConfig 实例。
     *
     * @return 字段已填充的配置实例
     */
    private EmbeddingConfig newConfig() {
        EmbeddingConfig config = new EmbeddingConfig();
        ReflectionTestUtils.setField(config, "baseUrl", "http://localhost:11434/v1");
        ReflectionTestUtils.setField(config, "apiKey", "test-key");
        ReflectionTestUtils.setField(config, "model", "bge-m3");
        ReflectionTestUtils.setField(config, "dimensions", 1024);
        return config;
    }

    @Nested
    @DisplayName("Bean 构造")
    class BeanConstruction {

        @Test
        @DisplayName("embeddingModel 返回非空 OpenAiEmbeddingModel")
        void should_returnNonNullModel_when_beanCreated() {
            EmbeddingModel model = newConfig().embeddingModel();

            assertThat(model).isNotNull().isInstanceOf(OpenAiEmbeddingModel.class);
        }

        @Test
        @DisplayName("配置值正确传入 options（模型/维度/端点/密钥）")
        void should_propagateConfigValues_when_beanCreated() {
            OpenAiEmbeddingModel model = (OpenAiEmbeddingModel) newConfig().embeddingModel();

            assertThat(model.getOptions().getModel()).isEqualTo("bge-m3");
            assertThat(model.getOptions().getDimensions()).isEqualTo(1024);
            assertThat(model.getOptions().getBaseUrl()).isEqualTo("http://localhost:11434/v1");
            assertThat(model.getOptions().getApiKey()).isEqualTo("test-key");
        }
    }

    @Nested
    @DisplayName("开关分支")
    class ConditionalBranches {

        @Test
        @DisplayName("enabled=true 时创建 EmbeddingModel Bean")
        void should_createBean_when_enabled() {
            contextRunner
                    .withPropertyValues("smartops.embedding.enabled=true")
                    .run(context -> assertThat(context).hasSingleBean(EmbeddingModel.class));
        }

        @Test
        @DisplayName("enabled 缺省（默认 false）时不创建 Bean")
        void should_notCreateBean_when_disabledByDefault() {
            contextRunner.run(context -> assertThat(context).doesNotHaveBean(EmbeddingModel.class));
        }

        @Test
        @DisplayName("enabled=false 时不创建 Bean")
        void should_notCreateBean_when_explicitlyDisabled() {
            contextRunner
                    .withPropertyValues("smartops.embedding.enabled=false")
                    .run(context -> assertThat(context).doesNotHaveBean(EmbeddingModel.class));
        }
    }
}
