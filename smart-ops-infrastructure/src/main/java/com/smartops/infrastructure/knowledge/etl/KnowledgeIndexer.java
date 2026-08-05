package com.smartops.infrastructure.knowledge.etl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 知识库索引器（阶段四 ETL，ADR-016）。
 *
 * <p>将切分好的块转换为 {@link Document}（幂等 id = nameUUID(source:index)，
 * metadata 记录 source/title）并批量写入 {@link VectorStore}。
 * 首次写入前用探针文本调用 {@link EmbeddingModel} 校验向量维度与配置一致，
 * 不一致立即抛出 {@link IllegalStateException} 快速失败（避免建出维度错误的索引）。</p>
 *
 * <p>仅在 {@link VectorStore} Bean 存在时创建（依赖 ES + embedding 双开关，ADR-015/016）。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Component
@ConditionalOnBean(VectorStore.class)
public class KnowledgeIndexer {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexer.class);

    /** 维度校验探针文本。 */
    static final String DIMENSION_PROBE = "维度探针";

    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private final int expectedDimensions;
    private boolean dimensionsValidated;

    /**
     * 构造索引器。
     *
     * @param vectorStore    向量库
     * @param embeddingModel embedding 模型（维度校验探针使用）
     * @param dimensions     期望向量维度（smartops.embedding.dimensions）
     */
    public KnowledgeIndexer(VectorStore vectorStore, EmbeddingModel embeddingModel,
                            @Value("${smartops.embedding.dimensions:1024}") int dimensions) {
        this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore 不能为 null");
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embeddingModel 不能为 null");
        this.expectedDimensions = dimensions;
    }

    /**
     * 索引一个文档的全部块。
     *
     * @param source 来源文件相对路径（写入 metadata）
     * @param chunks 切分结果，空列表时不发起写入
     * @return 实际写入的块数
     */
    public int index(String source, List<MarkdownChunker.Chunk> chunks) {
        if (chunks.isEmpty()) {
            return 0;
        }
        validateDimensionsOnce();
        List<Document> documents = chunks.stream()
                .map(chunk -> Document.builder()
                        .id(stableId(source, chunk.index()))
                        .text(chunk.content())
                        .metadata(Map.of("source", source, "title", chunk.title()))
                        .build())
                .toList();
        vectorStore.add(documents);
        log.info("知识库索引完成：source={}, chunks={}", source, documents.size());
        return documents.size();
    }

    /**
     * 生成幂等文档 id：同一 source + chunkIndex 重复 ETL 产生相同 id（覆盖写而非重复）。
     *
     * @param source 来源路径
     * @param index  块序号
     * @return 稳定的 UUID 字符串
     */
    static String stableId(String source, int index) {
        return UUID.nameUUIDFromBytes((source + ":" + index).getBytes(StandardCharsets.UTF_8)).toString();
    }

    /**
     * 首次写入前校验 embedding 输出维度与配置一致。
     */
    private void validateDimensionsOnce() {
        if (dimensionsValidated) {
            return;
        }
        float[] probe = embeddingModel.embed(DIMENSION_PROBE);
        if (probe.length != expectedDimensions) {
            throw new IllegalStateException(
                    "embedding 维度不匹配：期望 " + expectedDimensions + "，实际 " + probe.length
                            + "（检查 smartops.embedding.dimensions 与模型/索引 mapping 是否一致）");
        }
        dimensionsValidated = true;
    }
}
