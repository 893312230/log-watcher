package com.smartops.domain.topology.port;

import com.smartops.domain.topology.TopologyEdge;
import com.smartops.domain.topology.TopologyNode;
import java.util.List;
import java.util.Optional;

/**
 * 拓扑持久化端口（阶段七）。
 */
public interface TopologyRepository {
    List<TopologyNode> findAllNodes();
    TopologyNode saveNode(TopologyNode node);
    void deleteNode(Long id);

    List<TopologyEdge> findAllEdges();
    TopologyEdge saveEdge(TopologyEdge edge);
    void deleteEdge(Long id);

    Optional<TopologyNode> findNodeById(Long id);
}
