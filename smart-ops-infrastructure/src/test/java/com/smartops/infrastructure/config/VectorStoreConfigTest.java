package com.smartops.infrastructure.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import com.smartops.domain.knowledge.KnowledgeRetriever;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link VectorStoreConfig} 单元测试。
 *
 * <p>验证：开关两分支（disabled 无 Bean、enabled 但无 EmbeddingModel 时仅连接层 Bean）、
 * Bean 方法直接调用的装配正确性。全量 enabled+embedding 的上下文启动会触发
 * VectorStore 的 afterPropertiesSet 连接真实 ES，属于门控 IT 范围，不在此覆盖。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class VectorStoreConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(VectorStoreConfig.class));

    /**
     * 构造填充好配置值的 VectorStoreConfig 实例。
     *
     * @return 字段已填充的配置实例
     */
    private VectorStoreConfig newConfig() {
        VectorStoreConfig config = new VectorStoreConfig();
        ReflectionTestUtils.setField(config, "uris", "http://localhost:9200");
        ReflectionTestUtils.setField(config, "indexName", "smartops-knowledge");
        ReflectionTestUtils.setField(config, "dimensions", 1024);
        ReflectionTestUtils.setField(config, "rrfK", 60);
        return config;
    }

    @Nested
    @DisplayName("开关分支")
    class ConditionalBranches {

        @Test
        @DisplayName("enabled 缺省（默认 false）时不创建任何 Bean")
        void should_createNoBeans_when_disabledByDefault() {
            contextRunner.run(context -> {
                assertThat(context).doesNotHaveBean(Rest5Client.class);
                assertThat(context).doesNotHaveBean(ElasticsearchClient.class);
                assertThat(context).doesNotHaveBean(VectorStore.class);
                assertThat(context).doesNotHaveBean(KnowledgeRetriever.class);
            });
        }

        @Test
        @DisplayName("enabled=true 但无 EmbeddingModel 时仅创建连接层 Bean")
        void should_createOnlyConnectionBeans_when_embeddingMissing() {
            contextRunner
                    .withPropertyValues("smartops.elasticsearch.enabled=true")
                    .run(context -> {
                        assertThat(context).hasSingleBean(Rest5Client.class);
                        assertThat(context).hasSingleBean(ElasticsearchClient.class);
                        assertThat(context).doesNotHaveBean(VectorStore.class);
                        assertThat(context).doesNotHaveBean(KnowledgeRetriever.class);
                    });
        }
    }

    @Nested
    @DisplayName("Bean 装配")
    class BeanWiring {

        @Test
        @DisplayName("rest5Client/elasticsearchClient 返回非空实例")
        void should_createConnectionBeans_when_invoked() {
            VectorStoreConfig config = newConfig();
            Rest5Client rest5Client = config.rest5Client();

            assertThat(rest5Client).isNotNull();
            assertThat(config.elasticsearchClient(rest5Client)).isNotNull();
        }

        @Test
        @DisplayName("vectorStore 返回非空 VectorStore（build 不触网络）")
        void should_createVectorStore_when_invoked() {
            VectorStore vectorStore = newConfig().vectorStore(mock(Rest5Client.class), mock(EmbeddingModel.class));

            assertThat(vectorStore).isNotNull();
        }

        @Test
        @DisplayName("knowledgeRetriever 返回 EsKnowledgeRetriever 实例")
        void should_createRetriever_when_invoked() {
            KnowledgeRetriever retriever = newConfig().knowledgeRetriever(
                    mock(VectorStore.class), mock(ElasticsearchClient.class));

            assertThat(retriever).isNotNull().isInstanceOf(
                    com.smartops.infrastructure.knowledge.impl.EsKnowledgeRetriever.class);
        }
    }
}
