package com.smartops.domain.knowledge;

import java.util.List;

/**
 * 知识库检索端口。
 *
 * <p>两级混合检索（BM25 + 向量 + RRF，ADR-016）的领域端口，
 * 实现位于 smart-ops-infrastructure（{@code EsKnowledgeRetriever}），
 * 消费方为 agent-core 的 KnowledgeAgent。</p>
 *
 * <p><b>降级契约</b>：实现不得抛出异常——检索后端不可用、超时或任何失败
 * 均返回空列表，由调用方决定降级行为（KnowledgeAgent 保留"知识库未接入"前缀）。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public interface KnowledgeRetriever {

    /**
     * 按查询文本检索最相关的知识块。
     *
     * @param query 查询文本，非空
     * @param topK  最大返回条数，正数
     * @return 按相关度降序的知识块列表；检索失败或无命中时返回空列表
     */
    List<KnowledgeChunk> retrieve(String query, int topK);
}
