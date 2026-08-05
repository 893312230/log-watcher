package com.smartops.domain.knowledge;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 运维知识条目（阶段六知识库）。
 *
 * <p>记录一条运维问题及其根因分析、修复建议、处置意见。
 * 可关联告警（sourceAlertId）和服务器配置（serverConfigId），
 * 来源可以是 logwatch 自动分析结果、手动录入或外部导入。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public record KnowledgeEntry(
        Long id,
        String title,
        String errorPattern,
        String rootCause,
        String suggestion,
        List<String> actionItems,
        String category,
        List<String> tags,
        String source,
        Long sourceAlertId,
        Long serverConfigId,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {

    public static final int TITLE_MAX_LENGTH = 512;

    public KnowledgeEntry {
        Objects.requireNonNull(title, "标题不能为 null");
        if (title.isBlank()) throw new IllegalArgumentException("标题不能为空");
        if (title.length() > TITLE_MAX_LENGTH) throw new IllegalArgumentException(
                "标题最长 " + TITLE_MAX_LENGTH + " 字符");
        if (source == null || source.isBlank()) source = "MANUAL";
        actionItems = actionItems == null ? Collections.emptyList()
                : Collections.unmodifiableList(List.copyOf(actionItems));
        tags = tags == null ? Collections.emptyList()
                : Collections.unmodifiableList(List.copyOf(tags));
    }

    public static KnowledgeEntry create(String title, String errorPattern,
            String rootCause, String suggestion, List<String> actionItems,
            String category, List<String> tags, String source,
            Long sourceAlertId, Long serverConfigId, String createdBy, Instant now) {
        return new KnowledgeEntry(null, title, errorPattern, rootCause, suggestion,
                actionItems, category, tags, source, sourceAlertId, serverConfigId,
                createdBy, now, now);
    }

    public KnowledgeEntry withId(long newId) {
        return new KnowledgeEntry(newId, title, errorPattern, rootCause, suggestion,
                actionItems, category, tags, source, sourceAlertId, serverConfigId,
                createdBy, createdAt, updatedAt);
    }
}
