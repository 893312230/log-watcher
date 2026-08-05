package com.smartops.infrastructure.persistence.topology.impl;

import com.smartops.domain.topology.TopologyEdge;
import com.smartops.domain.topology.TopologyNode;
import com.smartops.domain.topology.port.TopologyRepository;
import com.smartops.infrastructure.persistence.topology.*;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public class TopologyRepositoryImpl implements TopologyRepository {
    private final TopologyNodeJpaRepository nodeRepo;
    private final TopologyEdgeJpaRepository edgeRepo;

    public TopologyRepositoryImpl(TopologyNodeJpaRepository nodeRepo, TopologyEdgeJpaRepository edgeRepo) {
        this.nodeRepo = nodeRepo; this.edgeRepo = edgeRepo;
    }

    @Override public List<TopologyNode> findAllNodes() { return nodeRepo.findAll().stream().map(this::toNode).toList(); }
    @Override public TopologyNode saveNode(TopologyNode n) { return toNode(nodeRepo.save(toEntity(n))); }
    @Override public void deleteNode(Long id) { nodeRepo.deleteById(id); }
    @Override public Optional<TopologyNode> findNodeById(Long id) { return nodeRepo.findById(id).map(this::toNode); }

    @Override public List<TopologyEdge> findAllEdges() { return edgeRepo.findAll().stream().map(this::toEdge).toList(); }
    @Override public TopologyEdge saveEdge(TopologyEdge e) { return toEdge(edgeRepo.save(toEntity(e))); }
    @Override public void deleteEdge(Long id) { edgeRepo.deleteById(id); }

    private TopologyNode toNode(TopologyNodeEntity e) { return new TopologyNode(e.getId(), e.getName(), e.getType(), e.getHost(), e.getStatus(), e.getMetadata()); }
    private TopologyNodeEntity toEntity(TopologyNode n) { TopologyNodeEntity e = new TopologyNodeEntity(); e.setId(n.id()); e.setName(n.name()); e.setType(n.type()); e.setHost(n.host()); e.setStatus(n.status()); e.setMetadata(n.metadata()); return e; }
    private TopologyEdge toEdge(TopologyEdgeEntity e) { return new TopologyEdge(e.getId(), e.getSourceId(), e.getTargetId(), e.getType()); }
    private TopologyEdgeEntity toEntity(TopologyEdge e) { TopologyEdgeEntity ee = new TopologyEdgeEntity(); ee.setId(e.id()); ee.setSourceId(e.sourceId()); ee.setTargetId(e.targetId()); ee.setType(e.type()); return ee; }
}
