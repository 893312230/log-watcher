package com.smartops.infrastructure.config;

import com.smartops.infrastructure.llm.LlmProvider;
import com.smartops.infrastructure.llm.LlmProviderRegistry;
import com.smartops.infrastructure.llm.LlmProviderRegistryImpl;
import com.smartops.infrastructure.llm.OpenAiLlmProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 多模型 Provider 装配配置（阶段五多模型抽象层）。
 *
 * <p>读取 {@code smartops.llm.providers.<name>.*} 配置块，逐条构造
 * {@link OpenAiLlmProvider}，汇总到 {@link LlmProviderRegistry} Bean。
 * 仅当 {@code smartops.llm.providers} 有配置项时激活。</p>
 *
 * <p>每个 Provider 对应独立的 ChatModel + ChatClient 实例，
 * 上层通过 {@link LlmProviderRegistry#getByName} 或 {@link LlmProviderRegistry#getToolCapable()}
 * 按场景选择。线程安全：Register 本身不可变，ChatClient/ChatModel 线程安全。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(prefix = "smartops.llm", name = "default-provider")
@EnableConfigurationProperties(ModelProviderProperties.class)
public class ModelProviderConfig {

    /** 单 Provider 缺失配置字段时的兜底最低温度（0.0）。 */
    private static final double DEFAULT_MIN_TEMPERATURE = 0.0;

    /** 单 Provider 缺失配置字段时的兜底最高温度（2.0）。 */
    private static final double DEFAULT_MAX_TEMPERATURE = 2.0;

    /**
     * 多模型 Provider 注册表 Bean。
     *
     * @param properties 多模型配置属性
     * @return Provider 注册表
     */
    @Bean
    public LlmProviderRegistry llmProviderRegistry(ModelProviderProperties properties) {
        String defaultName = properties.defaultProvider();
        Map<String, ModelProviderProperties.ProviderConfig> configs = properties.providers();
        if (configs == null || configs.isEmpty()) {
            throw new IllegalStateException(
                    "smartops.llm.providers 已激活但未配置任何 Provider");
        }
        List<LlmProvider> providers = new ArrayList<>();
        for (var entry : configs.entrySet()) {
            String name = entry.getKey();
            ModelProviderProperties.ProviderConfig cfg = entry.getValue();
            validate(name, cfg);
            providers.add(createProvider(name, cfg));
        }
        return new LlmProviderRegistryImpl(providers, defaultName);
    }

    /**
     * 创建单个 OpenAI 兼容 Provider。
     */
    private OpenAiLlmProvider createProvider(String name,
                                              ModelProviderProperties.ProviderConfig cfg) {
        double minTemp = cfg.minTemperature() > 0
                ? cfg.minTemperature() : DEFAULT_MIN_TEMPERATURE;
        double maxTemp = cfg.maxTemperature() > 0
                ? cfg.maxTemperature() : DEFAULT_MAX_TEMPERATURE;
        return new OpenAiLlmProvider(name, cfg.baseUrl(), cfg.apiKey(), cfg.model(),
                cfg.temperature(), cfg.supportsTools(), minTemp, maxTemp);
    }

    /**
     * 校验单个 Provider 配置。
     */
    private void validate(String name, ModelProviderProperties.ProviderConfig cfg) {
        if (cfg.baseUrl() == null || cfg.baseUrl().isBlank()) {
            throw new IllegalArgumentException(
                    "Provider '" + name + "': base-url 不能为空");
        }
        if (cfg.apiKey() == null || cfg.apiKey().isBlank()) {
            throw new IllegalArgumentException(
                    "Provider '" + name + "': api-key 不能为空");
        }
        if (cfg.model() == null || cfg.model().isBlank()) {
            throw new IllegalArgumentException(
                    "Provider '" + name + "': model 不能为空");
        }
        if (cfg.type() != null && !"openai".equals(cfg.type())) {
            throw new IllegalArgumentException(
                    "Provider '" + name + "': 不支持的类型 '" + cfg.type()
                            + "'，当前仅支持 openai");
        }
    }
}
