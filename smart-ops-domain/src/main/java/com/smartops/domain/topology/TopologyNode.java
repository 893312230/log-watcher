package com.smartops.domain.topology;

import java.util.Objects;

/**
 * 拓扑节点（阶段七服务拓扑图）。
 *
 * @param id       持久化 id
 * @param name     节点名称（服务名）
 * @param type     节点类型（SERVICE / DATABASE / QUEUE / EXTERNAL）
 * @param host     主机地址
 * @param status   当前状态（UP / DOWN / UNKNOWN）
 * @param metadata 扩展元数据（JSON）
 */
public record TopologyNode(
        Long id, String name, String type, String host,
        String status, String metadata
) {
    public TopologyNode {
        Objects.requireNonNull(name, "节点名称不能为 null");
        if (type == null || type.isBlank()) type = "SERVICE";
        if (status == null || status.isBlank()) status = "UNKNOWN";
    }
}
