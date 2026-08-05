package com.smartops.infrastructure.knowledge.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartops.domain.knowledge.KnowledgeChunk;
import com.smartops.domain.knowledge.KnowledgeRetriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ES 两级混合检索实现（BM25 文本路 + 向量路，客户端 RRF 融合）。
 *
 * <p>ADR-016：Spring AI 2.0.0 的 {@code ElasticsearchVectorStore} 未暴露 hybrid/RRF 能力，
 * 故采用回退路径——向量路走 {@link VectorStore#similaritySearch}，
 * BM25 路走 {@link ElasticsearchClient} 的 match 查询（字段 {@code content}），
 * 两路结果在客户端按 RRF 公式 {@code score = Σ 1/(rrfK + rank)} 融合。</p>
 *
 * <p>实现 {@link KnowledgeRetriever} 的降级契约：任何异常（ES 不可用、索引不存在、
 * embedding 服务失败）均记录 warn 日志并返回空列表，绝不向上抛异常。</p>
 *
 * <p>线程安全：无内部可变状态，两个客户端均为线程安全，Bean 单例可共享。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public class EsKnowledgeRetriever implements KnowledgeRetriever {

    private static final Logger log = LoggerFactory.getLogger(EsKnowledgeRetriever.class);

    /** ES 索引中文本字段名（Spring AI ES 向量库 mapping 固定为 content）。 */
    static final String CONTENT_FIELD = "content";

    /** ES 索引中元数据字段名（Spring AI ES 向量库 mapping 固定为 metadata 对象）。 */
    static final String METADATA_FIELD = "metadata";

    private final VectorStore vectorStore;
    private final ElasticsearchClient elasticsearchClient;
    private final String indexName;
    private final int rrfK;

    /**
     * 构造混合检索实现。
     *
     * @param vectorStore         Spring AI 向量库（向量路）
     * @param elasticsearchClient ES 高层客户端（BM25 文本路）
     * @param indexName           知识库索引名
     * @param rrfK                RRF 融合常数，正数
     */
    public EsKnowledgeRetriever(VectorStore vectorStore, ElasticsearchClient elasticsearchClient,
                                String indexName, int rrfK) {
        this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore 不能为 null");
        this.elasticsearchClient = Objects.requireNonNull(elasticsearchClient, "elasticsearchClient 不能为 null");
        this.indexName = Objects.requireNonNull(indexName, "indexName 不能为 null");
        if (rrfK <= 0) {
            throw new IllegalArgumentException("rrfK 必须为正数，实际: " + rrfK);
        }
        this.rrfK = rrfK;
    }

    /**
     * 执行两路检索并 RRF 融合。
     *
     * @param query 查询文本
     * @param topK  最大返回条数
     * @return 融合后按得分降序的知识块；任何失败返回空列表
     */
    @Override
    public List<KnowledgeChunk> retrieve(String query, int topK) {
        try {
            List<Document> vectorHits = vectorStore.similaritySearch(
                    SearchRequest.builder().query(query).topK(topK).build());
            SearchResponse<ObjectNode> bm25Hits = elasticsearchClient.search(s -> s
                            .index(indexName)
                            .size(topK)
                            .query(q -> q.match(m -> m.field(CONTENT_FIELD).query(query))),
                    ObjectNode.class);
            return fuse(vectorHits, bm25Hits, topK);
        } catch (Exception e) {
            log.warn("知识检索失败，降级返回空结果（index={}）: {}", indexName, e.getMessage());
            return List.of();
        }
    }

    /**
     * RRF 融合两路结果：score = Σ 1/(rrfK + rank)，rank 从 1 开始。
     *
     * @param vectorHits 向量路命中
     * @param bm25Hits   BM25 路命中
     * @param topK       截断条数
     * @return 融合排序后的知识块
     */
    private List<KnowledgeChunk> fuse(List<Document> vectorHits, SearchResponse<ObjectNode> bm25Hits, int topK) {
        Map<String, ChunkAccumulator> byId = new HashMap<>();

        for (int i = 0; i < vectorHits.size(); i++) {
            Document doc = vectorHits.get(i);
            ChunkAccumulator acc = byId.computeIfAbsent(doc.getId(), id -> new ChunkAccumulator());
            acc.content = doc.getText();
            acc.source = metadataString(doc.getMetadata(), "source");
            acc.title = metadataString(doc.getMetadata(), "title");
            acc.score += rrfScore(i);
        }

        List<Hit<ObjectNode>> hits = bm25Hits.hits().hits();
        for (int i = 0; i < hits.size(); i++) {
            Hit<ObjectNode> hit = hits.get(i);
            ChunkAccumulator acc = byId.computeIfAbsent(hit.id(), id -> new ChunkAccumulator());
            ObjectNode source = hit.source();
            if (source != null) {
                if (acc.content == null) {
                    acc.content = textValue(source.get(CONTENT_FIELD));
                }
                JsonNode metadata = source.get(METADATA_FIELD);
                if (metadata != null) {
                    if (acc.source == null) {
                        acc.source = textValue(metadata.get("source"));
                    }
                    if (acc.title == null) {
                        acc.title = textValue(metadata.get("title"));
                    }
                }
            }
            acc.score += rrfScore(i);
        }

        List<KnowledgeChunk> fused = new ArrayList<>();
        byId.forEach((id, acc) -> fused.add(new KnowledgeChunk(
                id,
                acc.content == null ? "" : acc.content,
                acc.source == null ? "" : acc.source,
                acc.title,
                acc.score)));
        fused.sort((a, b) -> Double.compare(b.score(), a.score()));
        return fused.size() > topK ? fused.subList(0, topK) : fused;
    }

    /**
     * 计算单路 RRF 得分。
     *
     * @param zeroBasedRank 该路排名（0 开始）
     * @return 1/(rrfK + rank + 1)
     */
    private double rrfScore(int zeroBasedRank) {
        return 1.0 / (rrfK + zeroBasedRank + 1);
    }

    /**
     * 从 Document 元数据取字符串值。
     *
     * @param metadata 元数据 Map
     * @param key      键
     * @return 字符串值，缺失或非字符串时为 null
     */
    private String metadataString(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value instanceof String s ? s : null;
    }

    /**
     * 从 JsonNode 取文本值。
     *
     * @param node 节点，可能为 null
     * @return 文本值，节点缺失或非文本时为 null
     */
    private String textValue(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : null;
    }

    /** 融合过程的中间累积态。 */
    private static final class ChunkAccumulator {
        private String content;
        private String source;
        private String title;
        private double score;
    }
}
