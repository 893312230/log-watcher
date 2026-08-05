package com.smartops.infrastructure.knowledge.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartops.domain.knowledge.KnowledgeChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link EsKnowledgeRetriever} 单元测试。
 *
 * <p>验证两路 RRF 融合（双路命中叠加、单路命中、全空）、降级契约
 * （向量路/文本路异常返回空表）、topK 截断与构造参数校验。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class EsKnowledgeRetrieverTest {

    private VectorStore vectorStore;
    private ElasticsearchClient esClient;
    private EsKnowledgeRetriever retriever;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        esClient = mock(ElasticsearchClient.class);
        retriever = new EsKnowledgeRetriever(vectorStore, esClient, "smartops-knowledge", 60);
    }

    /**
     * 构造向量路命中文档。
     *
     * @param id     文档 id
     * @param source 来源路径
     * @param title  标题
     * @return Document 实例
     */
    private Document vectorDoc(String id, String source, String title) {
        return Document.builder()
                .id(id)
                .text("内容-" + id)
                .metadata(Map.of("source", source, "title", title))
                .score(0.9)
                .build();
    }

    /**
     * 构造 BM25 路命中响应。
     *
     * @param hits 命中列表
     * @return SearchResponse 实例
     */
    private SearchResponse<ObjectNode> bm25Response(Hit<ObjectNode>... hits) {
        return SearchResponse.of(s -> s
                .took(1)
                .timedOut(false)
                .shards(sh -> sh.total(1).successful(1).skipped(0).failed(0))
                .hits(h -> h.hits(List.of(hits))));
    }

    /**
     * 构造单个 BM25 命中。
     *
     * @param id     文档 id
     * @param source 来源路径，可为 null
     * @param title  标题，可为 null
     * @return Hit 实例
     */
    private Hit<ObjectNode> bm25Hit(String id, String source, String title) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("content", "内容-" + id);
        ObjectNode metadata = node.putObject("metadata");
        if (source != null) {
            metadata.put("source", source);
        }
        if (title != null) {
            metadata.put("title", title);
        }
        return Hit.of(h -> h.index("smartops-knowledge").id(id).score(2.0).source(node));
    }

    /**
     * 构造无 source 的 BM25 命中。
     *
     * @param id 文档 id
     * @return Hit 实例
     */
    private Hit<ObjectNode> bm25HitWithoutSource(String id) {
        return Hit.of(h -> h.index("smartops-knowledge").id(id).score(1.0));
    }

    @Nested
    @DisplayName("RRF 融合")
    class RrfFusion {

        @Test
        @DisplayName("双路同时命中的文档得分叠加并排在最前")
        @SuppressWarnings("unchecked")
        void should_boostDocHitByBothLegs_when_fusing() throws Exception {
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(List.of(vectorDoc("a", "a.md", "A"), vectorDoc("b", "b.md", "B")));
            when(esClient.search(any(Function.class), eq(ObjectNode.class)))
                    .thenReturn(bm25Response(bm25Hit("a", "a.md", "A"), bm25Hit("c", "c.md", "C")));

            List<KnowledgeChunk> result = retriever.retrieve("重启流程", 5);

            assertThat(result).hasSize(3);
            assertThat(result.get(0).id()).isEqualTo("a");
            assertThat(result.get(0).score())
                    .isGreaterThan(result.get(1).score())
                    .isGreaterThan(result.get(2).score());
            assertThat(result.get(0).source()).isEqualTo("a.md");
            assertThat(result.get(0).title()).isEqualTo("A");
        }

        @Test
        @DisplayName("仅向量路命中时返回向量路结果")
        @SuppressWarnings("unchecked")
        void should_returnVectorOnly_when_bm25Empty() throws Exception {
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(List.of(vectorDoc("a", "a.md", "A")));
            when(esClient.search(any(Function.class), eq(ObjectNode.class)))
                    .thenReturn(bm25Response());

            List<KnowledgeChunk> result = retriever.retrieve("重启流程", 5);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo("a");
            assertThat(result.get(0).content()).isEqualTo("内容-a");
        }

        @Test
        @DisplayName("仅 BM25 路命中时返回 BM25 结果")
        @SuppressWarnings("unchecked")
        void should_returnBm25Only_when_vectorEmpty() throws Exception {
            when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
            when(esClient.search(any(Function.class), eq(ObjectNode.class)))
                    .thenReturn(bm25Response(bm25Hit("b", "b.md", "B")));

            List<KnowledgeChunk> result = retriever.retrieve("重启流程", 5);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo("b");
            assertThat(result.get(0).content()).isEqualTo("内容-b");
        }

        @Test
        @DisplayName("两路均空时返回空列表")
        @SuppressWarnings("unchecked")
        void should_returnEmpty_when_bothLegsEmpty() throws Exception {
            when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
            when(esClient.search(any(Function.class), eq(ObjectNode.class)))
                    .thenReturn(bm25Response());

            assertThat(retriever.retrieve("重启流程", 5)).isEmpty();
        }

        @Test
        @DisplayName("融合结果超过 topK 时按得分截断")
        @SuppressWarnings("unchecked")
        void should_truncateToTopK_when_resultsExceed() throws Exception {
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(List.of(vectorDoc("a", "a.md", "A"), vectorDoc("b", "b.md", "B")));
            when(esClient.search(any(Function.class), eq(ObjectNode.class)))
                    .thenReturn(bm25Response(bm25Hit("c", "c.md", "C")));

            List<KnowledgeChunk> result = retriever.retrieve("重启流程", 2);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("BM25 命中 source 为 null 时字段留空不抛异常")
        @SuppressWarnings("unchecked")
        void should_tolerateNullSource_when_hitHasNoSource() throws Exception {
            when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
            when(esClient.search(any(Function.class), eq(ObjectNode.class)))
                    .thenReturn(bm25Response(bm25HitWithoutSource("x")));

            List<KnowledgeChunk> result = retriever.retrieve("重启流程", 5);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).content()).isEmpty();
            assertThat(result.get(0).source()).isEmpty();
        }

        @Test
        @DisplayName("BM25 命中 metadata 缺字段时对应字段留空")
        @SuppressWarnings("unchecked")
        void should_leaveFieldsEmpty_when_metadataMissing() throws Exception {
            when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
            when(esClient.search(any(Function.class), eq(ObjectNode.class)))
                    .thenReturn(bm25Response(bm25Hit("y", null, null)));

            List<KnowledgeChunk> result = retriever.retrieve("重启流程", 5);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).source()).isEmpty();
            assertThat(result.get(0).title()).isEmpty();
            assertThat(result.get(0).content()).isEqualTo("内容-y");
        }
    }

    @Nested
    @DisplayName("降级契约")
    class Degradation {

        @Test
        @DisplayName("向量路抛异常时返回空列表")
        void should_returnEmpty_when_vectorLegThrows() {
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenThrow(new RuntimeException("Ollama 不可用"));

            assertThat(retriever.retrieve("重启流程", 5)).isEmpty();
        }

        @Test
        @DisplayName("BM25 路抛 IOException 时返回空列表")
        @SuppressWarnings("unchecked")
        void should_returnEmpty_when_bm25LegThrows() throws Exception {
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(List.of(vectorDoc("a", "a.md", "A")));
            when(esClient.search(any(Function.class), eq(ObjectNode.class)))
                    .thenThrow(new IOException("ES 连接拒绝"));

            assertThat(retriever.retrieve("重启流程", 5)).isEmpty();
        }
    }

    @Nested
    @DisplayName("构造校验")
    class Construction {

        @Test
        @DisplayName("vectorStore 为 null 时抛出 NullPointerException")
        void should_throwNPE_when_vectorStoreNull() {
            assertThatThrownBy(() -> new EsKnowledgeRetriever(null, esClient, "idx", 60))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("elasticsearchClient 为 null 时抛出 NullPointerException")
        void should_throwNPE_when_esClientNull() {
            assertThatThrownBy(() -> new EsKnowledgeRetriever(vectorStore, null, "idx", 60))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("indexName 为 null 时抛出 NullPointerException")
        void should_throwNPE_when_indexNameNull() {
            assertThatThrownBy(() -> new EsKnowledgeRetriever(vectorStore, esClient, null, 60))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rrfK 非正数时抛出 IllegalArgumentException")
        void should_throwIAE_when_rrfKNotPositive() {
            assertThatThrownBy(() -> new EsKnowledgeRetriever(vectorStore, esClient, "idx", 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("rrfK");
        }
    }
}
