package com.smartops.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AgentMode} 枚举测试。
 *
 * <p>验证 Agent 执行模式枚举的完整性。对应 agent.md 第六章 6.2 节覆盖率要求。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class AgentModeTest {

    @Test
    @DisplayName("枚举包含全部 2 种执行模式")
    void should_containAllModes_when_valuesCalled() {
        AgentMode[] modes = AgentMode.values();

        assertThat(modes).hasSize(2);
        assertThat(modes).containsExactly(AgentMode.REACT, AgentMode.PLAN_AND_SOLVE);
    }

    @Test
    @DisplayName("valueOf 按名称正确返回枚举常量")
    void should_returnCorrectMode_when_valueOfCalled() {
        assertThat(AgentMode.valueOf("REACT")).isEqualTo(AgentMode.REACT);
        assertThat(AgentMode.valueOf("PLAN_AND_SOLVE")).isEqualTo(AgentMode.PLAN_AND_SOLVE);
    }

    @Test
    @DisplayName("枚举名称可读")
    void should_haveReadableName_when_toStringCalled() {
        assertThat(AgentMode.REACT.toString()).isEqualTo("REACT");
        assertThat(AgentMode.PLAN_AND_SOLVE.toString()).isEqualTo("PLAN_AND_SOLVE");
    }
}
