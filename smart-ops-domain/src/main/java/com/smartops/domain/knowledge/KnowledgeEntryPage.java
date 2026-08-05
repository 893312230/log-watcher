package com.smartops.domain.knowledge;

import java.util.Collections;
import java.util.List;

/**
 * 知识条目分页结果。
 *
 * @param items 当前页条目
 * @param total 总条数
 * @param page  页码
 * @param size  每页大小
 */
public record KnowledgeEntryPage(
        List<KnowledgeEntry> items,
        long total,
        int page,
        int size
) {
    public KnowledgeEntryPage {
        items = items == null ? Collections.emptyList()
                : Collections.unmodifiableList(List.copyOf(items));
    }
}
