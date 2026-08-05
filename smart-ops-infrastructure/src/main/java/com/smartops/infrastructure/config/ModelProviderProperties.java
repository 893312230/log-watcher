package com.smartops.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * 多模型 Provider 配置属性（{@code smartops.llm.*}）。
 *
 * <p>阶段五多模型抽象层：在 {@code smartops.llm.providers} 下定义多个
 * LLM 端点，每个端点有独立的 base-url/api-key/model/温度/工具支持标志。</p>
 *
 * <p>线程安全：record 不可变，但 Map 内容的不可变性由 Boot 绑定层保证。</p>
 *
 * @author smartops
 * @since 1.0.0
 *
 * @param defaultProvider 默认 Provider 名称
 * @param providers       Provider 映射（key = Provider 名称）
 */
@ConfigurationProperties(prefix = "smartops.llm")
public record ModelProviderProperties(
        String defaultProvider,
        Map<String, ProviderConfig> providers
) {

    /**
     * 单个 Provider 的配置。
     *
     * @param type           Provider 类型（当前仅 openai）
     * @param baseUrl        API 端点地址
     * @param apiKey         API 密钥
     * @param model          模型名
     * @param temperature    默认采样温度
     * @param supportsTools  是否支持工具调用
     * @param minTemperature 最低允许温度
     * @param maxTemperature 最高允许温度
     */
    public record ProviderConfig(
            String type,
            String baseUrl,
            String apiKey,
            String model,
            double temperature,
            boolean supportsTools,
            double minTemperature,
            double maxTemperature
    ) {
    }
}
