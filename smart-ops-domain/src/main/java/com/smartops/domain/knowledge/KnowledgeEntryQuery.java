package com.smartops.domain.knowledge;

/**
 * 知识条目查询条件。
 *
 * @param keyword   搜索关键字（模糊匹配 title/rootCause/suggestion）
 * @param category  按分类过滤
 * @param source    按来源过滤
 * @param page      页码（从 0 开始）
 * @param size      每页大小（1-100）
 */
public record KnowledgeEntryQuery(
        String keyword,
        String category,
        String source,
        int page,
        int size
) {
    public KnowledgeEntryQuery {
        if (page < 0) page = 0;
        if (size < 1 || size > 100) size = 20;
    }
}
