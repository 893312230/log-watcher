package com.smartops.domain.topology;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link TopologyNode} 与 {@link TopologyEdge} 单元测试。
 *
 * @author smartops
 * @since 1.0.0
 */
class TopologyModelTest {

    @Test
    @DisplayName("节点类型/状态缺省时取默认值")
    void should_defaultTypeAndStatus_when_blank() {
        TopologyNode nulls = new TopologyNode(1L, "svc", null, "h1", null, null);
        assertThat(nulls.type()).isEqualTo("SERVICE");
        assertThat(nulls.status()).isEqualTo("UNKNOWN");

        TopologyNode blanks = new TopologyNode(2L, "svc", " ", "h1", " ", null);
        assertThat(blanks.type()).isEqualTo("SERVICE");
        assertThat(blanks.status()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("节点显式类型/状态保持不变")
    void should_keepTypeAndStatus_when_provided() {
        TopologyNode node = new TopologyNode(3L, "db", "DATABASE", "h2", "UP", "{}");
        assertThat(node.type()).isEqualTo("DATABASE");
        assertThat(node.status()).isEqualTo("UP");
    }

    @Test
    @DisplayName("节点名称为 null 时抛出异常")
    void should_throw_when_nodeNameNull() {
        assertThatThrownBy(() -> new TopologyNode(null, null, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("边类型缺省时取 DEPENDS_ON，显式值保持")
    void should_defaultEdgeType_when_blank() {
        assertThat(new TopologyEdge(1L, 1L, 2L, null).type()).isEqualTo("DEPENDS_ON");
        assertThat(new TopologyEdge(1L, 1L, 2L, " ").type()).isEqualTo("DEPENDS_ON");
        assertThat(new TopologyEdge(1L, 1L, 2L, "CALLS").type()).isEqualTo("CALLS");
    }

    @Test
    @DisplayName("边源/目标节点为 null 时抛出异常")
    void should_throw_when_edgeEndpointNull() {
        assertThatThrownBy(() -> new TopologyEdge(null, null, 2L, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TopologyEdge(null, 1L, null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
