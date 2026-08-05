package com.smartops.infrastructure.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import com.smartops.domain.knowledge.KnowledgeRetriever;
import com.smartops.infrastructure.knowledge.impl.EsKnowledgeRetriever;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStoreOptions;
import org.springframework.ai.vectorstore.elasticsearch.SimilarityFunction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

/**
 * Elasticsearch 向量库与混合检索配置（阶段四，ADR-016）。
 *
 * <p>创建四个 Bean：{@link Rest5Client}（低层连接）、{@link ElasticsearchClient}（BM25 文本路）、
 * {@link VectorStore}（Spring AI 向量路，initializeSchema 自动建索引）、
 * {@link KnowledgeRetriever}（两路 RRF 融合检索实现）。</p>
 *
 * <p>默认关闭（{@code smartops.elasticsearch.enabled=false}）。
 * {@link VectorStore} 依赖 {@link EmbeddingModel}（ADR-015，默认同样关闭），
 * 两级开关任一关闭时对应 Bean 不创建，KnowledgeAgent 走降级路径。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(name = "smartops.elasticsearch.enabled", havingValue = "true")
public class VectorStoreConfig {

    /** ES 地址，默认 docker-compose 单节点。 */
    @Value("${smartops.elasticsearch.uris:http://localhost:9200}")
    private String uris;

    /** 知识库索引名。 */
    @Value("${smartops.elasticsearch.index:smartops-knowledge}")
    private String indexName;

    /** 向量维度，须与 embedding 模型输出一致（bge-m3 为 1024）。 */
    @Value("${smartops.embedding.dimensions:1024}")
    private int dimensions;

    /** RRF 融合常数。 */
    @Value("${smartops.elasticsearch.rrf-k:60}")
    private int rrfK;

    /**
     * 构建 ES 低层连接客户端。
     *
     * @return Rest5Client 实例，随容器关闭释放
     */
    @Bean(destroyMethod = "close")
    public Rest5Client rest5Client() {
        return Rest5Client.builder(URI.create(uris)).build();
    }

    /**
     * 构建 ES 高层 API 客户端（BM25 文本路使用）。
     *
     * @param rest5Client 低层连接客户端
     * @return ElasticsearchClient 实例
     */
    @Bean
    public ElasticsearchClient elasticsearchClient(Rest5Client rest5Client) {
        return new ElasticsearchClient(new Rest5ClientTransport(rest5Client, new JacksonJsonpMapper()));
    }

    /**
     * 构建 Spring AI 向量库（向量路）。
     *
     * <p>initializeSchema=true：首次启动自动创建索引（content 文本 + embedding dense_vector）。</p>
     *
     * @param rest5Client    低层连接客户端
     * @param embeddingModel embedding 模型（ADR-015）
     * @return VectorStore 实例
     */
    @Bean
    @ConditionalOnBean(EmbeddingModel.class)
    public VectorStore vectorStore(Rest5Client rest5Client, EmbeddingModel embeddingModel) {
        ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
        options.setIndexName(indexName);
        options.setDimensions(dimensions);
        options.setSimilarity(SimilarityFunction.cosine);
        return ElasticsearchVectorStore.builder(rest5Client, embeddingModel)
                .options(options)
                .initializeSchema(true)
                .build();
    }

    /**
     * 构建两级混合检索实现（BM25 + 向量，客户端 RRF 融合）。
     *
     * @param vectorStore         向量库
     * @param elasticsearchClient ES 高层客户端
     * @return KnowledgeRetriever 实例
     */
    @Bean
    @ConditionalOnBean(VectorStore.class)
    public KnowledgeRetriever knowledgeRetriever(VectorStore vectorStore,
                                                 ElasticsearchClient elasticsearchClient) {
        return new EsKnowledgeRetriever(vectorStore, elasticsearchClient, indexName, rrfK);
    }
}
