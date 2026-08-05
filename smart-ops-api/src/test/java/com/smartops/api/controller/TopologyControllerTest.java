package com.smartops.api.controller;

import com.smartops.domain.topology.TopologyEdge;
import com.smartops.domain.topology.TopologyNode;
import com.smartops.domain.topology.port.TopologyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link TopologyController} Web 层测试。
 *
 * @author smartops
 * @since 1.0.0
 */
@WebMvcTest(TopologyController.class)
class TopologyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TopologyRepository repo;

    private static TopologyNode node(long id, String name) {
        return new TopologyNode(id, name, "SERVICE", "10.0.0." + id, "UP", null);
    }

    @Test
    @DisplayName("GET /api/topology 返回节点与边")
    void should_returnNodesAndEdges() throws Exception {
        when(repo.findAllNodes()).thenReturn(List.of(node(1L, "gateway")));
        when(repo.findAllEdges()).thenReturn(List.of(new TopologyEdge(1L, 1L, 2L, "CALLS")));

        mockMvc.perform(get("/api/topology"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes[0].name").value("gateway"))
                .andExpect(jsonPath("$.edges[0].type").value("CALLS"));
    }

    @Test
    @DisplayName("POST /api/topology/nodes 创建节点")
    void should_addNode() throws Exception {
        when(repo.saveNode(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/topology/nodes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"order-service\",\"type\":\"SERVICE\",\"host\":\"10.0.0.2\",\"status\":\"UP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("order-service"));
    }

    @Test
    @DisplayName("DELETE /api/topology/nodes/{id} 删除节点")
    void should_deleteNode() throws Exception {
        mockMvc.perform(delete("/api/topology/nodes/5")).andExpect(status().isOk());

        verify(repo).deleteNode(5L);
    }

    @Test
    @DisplayName("POST /api/topology/edges 创建边")
    void should_addEdge() throws Exception {
        when(repo.findNodeById(1L)).thenReturn(java.util.Optional.of(node(1L, "gateway")));
        when(repo.findNodeById(2L)).thenReturn(java.util.Optional.of(node(2L, "order-service")));
        when(repo.saveEdge(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/topology/edges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceId\":1,\"targetId\":2,\"type\":\"DEPENDS_ON\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceId").value(1))
                .andExpect(jsonPath("$.type").value("DEPENDS_ON"));
    }

    @Test
    @DisplayName("POST /api/topology/edges 缺少 sourceId → 400")
    void should_return400_when_edgeParamMissing() throws Exception {
        mockMvc.perform(post("/api/topology/edges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetId\":2,\"type\":\"DEPENDS_ON\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/topology/edges 节点不存在 → 404")
    void should_return404_when_edgeNodeMissing() throws Exception {
        when(repo.findNodeById(1L)).thenReturn(java.util.Optional.of(node(1L, "gateway")));
        when(repo.findNodeById(9L)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(post("/api/topology/edges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceId\":1,\"targetId\":9,\"type\":\"CALLS\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/topology/edges/{id} 删除边")
    void should_deleteEdge() throws Exception {
        mockMvc.perform(delete("/api/topology/edges/7")).andExpect(status().isOk());

        verify(repo).deleteEdge(7L);
    }

    @Test
    @DisplayName("GET /api/topology/impact 命中节点 → 返回上下游")
    void should_returnImpact_when_nodeFound() throws Exception {
        when(repo.findAllNodes()).thenReturn(List.of(
                node(1L, "gateway"), node(2L, "order-service"), node(3L, "order-db")));
        when(repo.findAllEdges()).thenReturn(List.of(
                new TopologyEdge(1L, 1L, 2L, "CALLS"),
                new TopologyEdge(2L, 2L, 3L, "DEPENDS_ON"),
                new TopologyEdge(3L, 99L, 2L, "CALLS")));

        mockMvc.perform(get("/api/topology/impact").param("nodeName", "order-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.node.name").value("order-service"))
                .andExpect(jsonPath("$.upstream[0].name").value("gateway"))
                .andExpect(jsonPath("$.downstream[0].name").value("order-db"));
    }

    @Test
    @DisplayName("GET /api/topology/impact 未命中 → found=false")
    void should_returnNotFound_when_nodeMissing() throws Exception {
        when(repo.findAllNodes()).thenReturn(List.of(node(1L, "gateway")));
        when(repo.findAllEdges()).thenReturn(List.of());

        mockMvc.perform(get("/api/topology/impact").param("nodeName", "nothing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(false));
    }
}
