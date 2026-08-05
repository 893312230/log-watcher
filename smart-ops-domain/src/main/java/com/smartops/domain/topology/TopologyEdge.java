package com.smartops.domain.topology;

import java.util.Objects;

/**
 * 拓扑边（阶段七）。
 *
 * @param id       持久化 id
 * @param sourceId 源节点 id
 * @param targetId 目标节点 id
 * @param type     关系类型（DEPENDS_ON / CALLS / PUBLISHES_TO）
 */
public record TopologyEdge(
        Long id, Long sourceId, Long targetId, String type
) {
    public TopologyEdge {
        Objects.requireNonNull(sourceId, "源节点不能为 null");
        Objects.requireNonNull(targetId, "目标节点不能为 null");
        if (type == null || type.isBlank()) type = "DEPENDS_ON";
    }
}
