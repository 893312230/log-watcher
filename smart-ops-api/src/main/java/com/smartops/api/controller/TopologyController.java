package com.smartops.api.controller;

import com.smartops.domain.topology.TopologyEdge;
import com.smartops.domain.topology.TopologyNode;
import com.smartops.domain.topology.port.TopologyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/topology")
public class TopologyController {

    private final TopologyRepository repo;

    public TopologyController(TopologyRepository repo) { this.repo = repo; }

    @GetMapping
    public Map<String, Object> get() {
        return Map.of("nodes", repo.findAllNodes(), "edges", repo.findAllEdges());
    }

    @PostMapping("/nodes")
    public TopologyNode addNode(@RequestBody Map<String, String> body) {
        return repo.saveNode(new TopologyNode(null,
                body.get("name"), body.get("type"), body.get("host"),
                body.get("status"), body.get("metadata")));
    }

    @DeleteMapping("/nodes/{id}") public void deleteNode(@PathVariable Long id) { repo.deleteNode(id); }

    @PostMapping("/edges")
    public TopologyEdge addEdge(@RequestBody Map<String, Object> body) {
        Object sourceId = body.get("sourceId");
        Object targetId = body.get("targetId");
        if (!(sourceId instanceof Number) || !(targetId instanceof Number)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sourceId/targetId 必填且为数字");
        }
        long src = ((Number) sourceId).longValue();
        long tgt = ((Number) targetId).longValue();
        if (repo.findNodeById(src).isEmpty() || repo.findNodeById(tgt).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "拓扑节点不存在: " + src + " -> " + tgt);
        }
        return repo.saveEdge(new TopologyEdge(null, src, tgt, (String) body.get("type")));
    }

    @DeleteMapping("/edges/{id}") public void deleteEdge(@PathVariable Long id) { repo.deleteEdge(id); }
    @GetMapping("/impact")
    public Map<String, Object> impact(@RequestParam String nodeName) {
        var nodes = repo.findAllNodes();
        var edges = repo.findAllEdges();
        var node = nodes.stream().filter(n -> n.name().contains(nodeName)).findFirst();
        if (node.isEmpty()) return Map.of("found", false);
        var upstream = edges.stream().filter(e -> e.targetId().equals(node.get().id()))
                .map(e -> nodes.stream().filter(n -> n.id().equals(e.sourceId())).findFirst())
                .filter(java.util.Optional::isPresent).map(java.util.Optional::get).toList();
        var downstream = edges.stream().filter(e -> e.sourceId().equals(node.get().id()))
                .map(e -> nodes.stream().filter(n -> n.id().equals(e.targetId())).findFirst())
                .filter(java.util.Optional::isPresent).map(java.util.Optional::get).toList();
        return Map.of("found", true, "node", node.get(), "upstream", upstream, "downstream", downstream);
    }
}
