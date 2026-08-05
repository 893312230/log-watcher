package com.smartops.agent.logwatch;

import com.smartops.domain.topology.TopologyEdge;
import com.smartops.domain.topology.TopologyNode;
import com.smartops.domain.topology.port.TopologyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link TopologyEnricher} 单元测试（拓扑仓库桩化）。
 *
 * @author smartops
 * @since 1.0.0
 */
class TopologyEnricherTest {

    private TopologyRepository repo;
    private TopologyEnricher enricher;

    @BeforeEach
    void setUp() {
        repo = mock(TopologyRepository.class);
        enricher = new TopologyEnricher(repo);
    }

    private TopologyNode node(long id, String name, String host, String status) {
        return new TopologyNode(id, name, "SERVICE", host, status, null);
    }

    @Test
    @DisplayName("命中节点且有上下游 → 输出完整拓扑上下文")
    void should_renderFullContext_when_matchedWithNeighbors() {
        TopologyNode gw = node(1L, "gateway", "10.0.0.1", "UP");
        TopologyNode order = node(2L, "order-service", "10.0.0.2", "DOWN");
        TopologyNode db = node(3L, "order-db", "10.0.0.3", "UP");
        when(repo.findAllNodes()).thenReturn(List.of(gw, order, db));
        when(repo.findAllEdges()).thenReturn(List.of(
                new TopologyEdge(1L, 1L, 2L, "CALLS"),
                new TopologyEdge(2L, 2L, 3L, "DEPENDS_ON")));

        String ctx = enricher.enrich("/var/log/order-service/app.log");

        assertThat(ctx).contains("【拓扑上下文】")
                .contains("order-service").contains("DOWN")
                .contains("上游依赖方: gateway(UP)")
                .contains("下游被依赖方: order-db(UP)");
    }

    @Test
    @DisplayName("命中节点但无关联边 → 仅输出受影响服务行")
    void should_renderServiceOnly_when_noEdges() {
        TopologyNode order = node(2L, "order-service", "10.0.0.2", "UP");
        when(repo.findAllNodes()).thenReturn(List.of(order));
        when(repo.findAllEdges()).thenReturn(List.of());

        String ctx = enricher.enrich("order-service error");

        assertThat(ctx).contains("受影响服务: order-service")
                .doesNotContain("上游依赖方").doesNotContain("下游被依赖方");
    }

    @Test
    @DisplayName("主机地址也可命中节点；取名称最长匹配")
    void should_matchByHostAndPreferLongestName() {
        TopologyNode shortName = node(1L, "order", "10.0.0.9", "UP");
        TopologyNode longName = node(2L, "order-service", "10.0.0.2", "UP");
        when(repo.findAllNodes()).thenReturn(List.of(shortName, longName));
        when(repo.findAllEdges()).thenReturn(List.of());

        assertThat(enricher.enrich("10.0.0.2 down")).contains("order-service");
    }

    @Test
    @DisplayName("无匹配节点 → 空串")
    void should_returnEmpty_when_noMatch() {
        when(repo.findAllNodes()).thenReturn(List.of(node(1L, "gateway", "10.0.0.1", "UP")));
        when(repo.findAllEdges()).thenReturn(List.of());

        assertThat(enricher.enrich("unrelated.log")).isEmpty();
    }

    @Test
    @DisplayName("边指向不存在的节点 → 该方向被跳过")
    void should_skipDanglingEdge_when_nodeMissing() {
        TopologyNode order = node(2L, "order-service", "10.0.0.2", "UP");
        when(repo.findAllNodes()).thenReturn(List.of(order));
        when(repo.findAllEdges()).thenReturn(List.of(
                new TopologyEdge(1L, 99L, 2L, "CALLS"),
                new TopologyEdge(2L, 2L, 98L, "DEPENDS_ON")));

        String ctx = enricher.enrich("order-service boom");

        assertThat(ctx).contains("受影响服务: order-service")
                .doesNotContain("上游依赖方").doesNotContain("下游被依赖方");
    }

    @Test
    @DisplayName("仓库异常 → 返回空串不抛出")
    void should_returnEmpty_when_repoThrows() {
        when(repo.findAllNodes()).thenThrow(new RuntimeException("db down"));

        assertThat(enricher.enrich("order-service")).isEmpty();
    }
}
