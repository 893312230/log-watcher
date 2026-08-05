package com.smartops.api.dto;

import com.smartops.domain.knowledge.KnowledgeEntryPage;

import java.util.List;

public record KnowledgeEntryPageView(
        List<KnowledgeEntryView> items, long total, int page, int size
) {
    public static KnowledgeEntryPageView from(KnowledgeEntryPage p) {
        return new KnowledgeEntryPageView(
                p.items().stream().map(KnowledgeEntryView::from).toList(),
                p.total(), p.page(), p.size());
    }
}
