package com.smartops.domain.knowledge;

import java.util.Objects;

/**
 * 知识库检索命中的文档块。
 *
 * <p>两级混合检索（BM25 + 向量，RRF 融合，ADR-016）的统一输出格式，
 * 由 {@link KnowledgeRetriever} 返回，供 KnowledgeAgent 拼装 RAG 上下文。</p>
 *
 * <p>线程安全：record 不可变。</p>
 *
 * @author smartops
 * @since 1.0.0
 *
 * @param id      文档块唯一标识（ETL 幂等键：source + chunkIndex 哈希）
 * @param content 文档块正文内容
 * @param source  来源文件相对路径，如 runbooks/restart-order-service.md
 * @param title   文档块所属标题（Markdown 标题感知切分时记录）
 * @param score   RRF 融合后的相关度得分，越大越相关
 */
public record KnowledgeChunk(
        String id,
        String content,
        String source,
        String title,
        double score
) {

    /**
     * 紧凑构造器：必填字段非空校验，title 归一化。
     *
     * @param id      文档块唯一标识
     * @param content 文档块正文
     * @param source  来源文件路径
     * @param title   所属标题，null 时归一化为空串
     * @param score   相关度得分
     */
    public KnowledgeChunk {
        Objects.requireNonNull(id, "文档块 id 不能为 null");
        Objects.requireNonNull(content, "文档块内容不能为 null");
        Objects.requireNonNull(source, "来源路径不能为 null");
        title = title == null ? "" : title;
    }
}
