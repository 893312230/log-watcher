package com.smartops.infrastructure.config;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Embedding 配置类（阶段四 RAG 向量路，ADR-015）。
 *
 * <p>DeepSeek 不提供 embedding API，本配置通过独立的 {@code smartops.embedding.*}
 * 命名空间接入 OpenAI 兼容端点（默认本地 Ollama bge-m3），
 * 不复用聊天模型的 {@code spring.ai.openai.*} 配置（两者 base-url/密钥/模型均不同）。</p>
 *
 * <p>默认关闭（{@code smartops.embedding.enabled=false}）：无 Ollama 环境应用照常启动，
 * 此时无 {@link EmbeddingModel} Bean，依赖方（向量库、ETL）同步降级。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(name = "smartops.embedding.enabled", havingValue = "true")
public class EmbeddingConfig {

    /** OpenAI 兼容端点地址，默认本地 Ollama。 */
    @Value("${smartops.embedding.base-url:http://localhost:11434/v1}")
    private String baseUrl;

    /** 端点密钥；Ollama 不校验，占位值即可。 */
    @Value("${smartops.embedding.api-key:ollama}")
    private String apiKey;

    /** embedding 模型名，默认 bge-m3。 */
    @Value("${smartops.embedding.model:bge-m3}")
    private String model;

    /** 向量维度，bge-m3 输出 1024 维，须与 ES dense_vector mapping 一致。 */
    @Value("${smartops.embedding.dimensions:1024}")
    private int dimensions;

    /**
     * 构建独立的 EmbeddingModel Bean。
     *
     * <p>手工构造 {@link OpenAiEmbeddingModel}，端点/密钥/模型/维度全部来自
     * {@code smartops.embedding.*}；构造过程不发起网络请求（首次调用时才连接端点）。</p>
     *
     * @return 配置好的 EmbeddingModel 实例
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .model(model)
                .dimensions(dimensions)
                .build();
        return new OpenAiEmbeddingModel(MetadataMode.EMBED, options);
    }
}
