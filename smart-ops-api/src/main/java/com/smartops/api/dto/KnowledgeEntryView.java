package com.smartops.api.dto;

import com.smartops.domain.knowledge.KnowledgeEntry;

import java.time.Instant;
import java.util.List;

public record KnowledgeEntryView(
        Long id, String title, String errorPattern, String rootCause,
        String suggestion, List<String> actionItems, String category,
        List<String> tags, String source, Long sourceAlertId,
        Long serverConfigId, String createdBy,
        Instant createdAt, Instant updatedAt
) {
    public static KnowledgeEntryView from(KnowledgeEntry e) {
        return new KnowledgeEntryView(e.id(), e.title(), e.errorPattern(),
                e.rootCause(), e.suggestion(), e.actionItems(), e.category(),
                e.tags(), e.source(), e.sourceAlertId(), e.serverConfigId(),
                e.createdBy(), e.createdAt(), e.updatedAt());
    }
}
