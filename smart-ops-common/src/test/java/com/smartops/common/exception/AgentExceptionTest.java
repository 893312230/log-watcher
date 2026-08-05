package com.smartops.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AgentException} 单元测试。
 *
 * <p>验证错误码必填、消息与根因传递等契约，
 * 对应 agent.md 第五章 5.3 节异常分层规范。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class AgentExceptionTest {

    @Test
    @DisplayName("构造异常时正确携带错误码与消息")
    void should_carryErrorCodeAndMessage_when_constructed() {
        AgentException ex = new AgentException("AGENT_001", "任务执行失败");

        assertThat(ex.getErrorCode()).isEqualTo("AGENT_001");
        assertThat(ex.getMessage()).isEqualTo("任务执行失败");
    }

    @Test
    @DisplayName("构造异常时正确携带根因")
    void should_carryCause_when_constructedWithCause() {
        Throwable rootCause = new IllegalStateException("连接超时");

        AgentException ex = new AgentException("AGENT_002", "LLM 调用失败", rootCause);

        assertThat(ex.getCause()).isSameAs(rootCause);
    }

    @Test
    @DisplayName("错误码为 null 时抛出 IllegalArgumentException")
    void should_throwIllegalArgument_when_errorCodeIsNull() {
        assertThatThrownBy(() -> new AgentException(null, "消息"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("错误码不能为空");
    }

    @Test
    @DisplayName("错误码为空白字符串时抛出 IllegalArgumentException")
    void should_throwIllegalArgument_when_errorCodeIsBlank() {
        assertThatThrownBy(() -> new AgentException("   ", "消息"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("错误码不能为空");
    }

    @Test
    @DisplayName("AgentException 是 RuntimeException 子类，可在运行时直接抛出")
    void should_beRuntimeException_when_checked() {
        AgentException ex = new AgentException("AGENT_003", "测试");

        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("带根因构造时错误码为 null 也抛出 IllegalArgumentException")
    void should_throwIllegalArgument_when_errorCodeIsNullAndHasCause() {
        Throwable cause = new IllegalStateException("根因");

        assertThatThrownBy(() -> new AgentException(null, "消息", cause))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("错误码不能为空");
    }

    @Test
    @DisplayName("带根因构造时错误码为空白也抛出 IllegalArgumentException")
    void should_throwIllegalArgument_when_errorCodeIsBlankAndHasCause() {
        Throwable cause = new IllegalStateException("根因");

        assertThatThrownBy(() -> new AgentException("   ", "消息", cause))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("错误码不能为空");
    }

    @Test
    @DisplayName("带根因构造时正确携带错误码、消息与根因")
    void should_carryAllFields_when_constructedWithCause() {
        Throwable cause = new IllegalStateException("连接超时");

        AgentException ex = new AgentException("AGENT_004", "LLM 调用失败", cause);

        assertThat(ex.getErrorCode()).isEqualTo("AGENT_004");
        assertThat(ex.getMessage()).isEqualTo("LLM 调用失败");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("SecurityViolationException 是 AgentException 子类")
    void should_beAgentException_when_securityViolation() {
        SecurityViolationException ex = new SecurityViolationException("SECURITY_INJECTION", "检测到 Prompt 注入");

        assertThat(ex).isInstanceOf(AgentException.class);
        assertThat(ex.getErrorCode()).isEqualTo("SECURITY_INJECTION");
    }
}
