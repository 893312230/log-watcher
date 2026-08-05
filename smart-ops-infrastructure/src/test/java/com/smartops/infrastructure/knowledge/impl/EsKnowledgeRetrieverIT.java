package com.smartops.infrastructure.knowledge.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import com.smartops.domain.knowledge.KnowledgeChunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link EsKnowledgeRetriever} 薄集成测试（BM25 文本路真实 ES）。
 *
 * <p>仅当环境变量 {@code SMARTOPS_IT=true} 时运行（ADR-016 测试策略）：
 * 需要 docker-compose 的 ES 已启动（localhost:9200）。
 * 向量路以 mock VectorStore 返回空表代替（真实向量路另需本地 Ollama bge-m3，
 * 属端到端冒烟范围）。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@EnabledIfEnvironmentVariable(named = "SMARTOPS_IT", matches = "true")
class EsKnowledgeRetrieverIT {

    /**
     * BM25 文本路真实 ES 往返：写入文档 → match 查询命中 → 字段正确映射。
     */
    @Test
    @DisplayName("真实 ES：BM25 路写入后可检索命中")
    void should_retrieveBm25Hit_when_realEs() throws Exception {
        String index = "smartops-knowledge-it";
        try (Rest5Client rest5Client = Rest5Client.builder(URI.create("http://localhost:9200")).build()) {
            ElasticsearchClient esClient = new ElasticsearchClient(
                    new Rest5ClientTransport(rest5Client, new JacksonJsonpMapper()));
            esClient.index(i -> i.index(index).id("it-1").document(Map.of(
                    "content", "重启订单服务的标准流程：先摘流量再重启",
                    "metadata", Map.of("source", "runbooks/restart.md", "title", "重启流程"))));
            esClient.indices().refresh(r -> r.index(index));

            VectorStore vectorStore = mock(VectorStore.class);
            when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
            EsKnowledgeRetriever retriever = new EsKnowledgeRetriever(vectorStore, esClient, index, 60);

            List<KnowledgeChunk> result = retriever.retrieve("重启订单服务", 5);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo("it-1");
            assertThat(result.get(0).content()).contains("重启订单服务");
            assertThat(result.get(0).source()).isEqualTo("runbooks/restart.md");

            esClient.indices().delete(d -> d.index(index));
        }
    }
}
