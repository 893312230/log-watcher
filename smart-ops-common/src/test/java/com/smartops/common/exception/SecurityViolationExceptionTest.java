package com.smartops.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SecurityViolationException} 单元测试。
 *
 * <p>验证安全违规异常的错误码传递、根因关联、前缀常量等契约。
 * 对应 agent.md 第五章 5.3 节异常分层规范。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class SecurityViolationExceptionTest {

    @Test
    @DisplayName("构造异常时正确携带错误码与消息")
    void should_carryErrorCodeAndMessage_when_constructed() {
        SecurityViolationException ex = new SecurityViolationException("SECURITY_INJECTION", "检测到 Prompt 注入");

        assertThat(ex.getErrorCode()).isEqualTo("SECURITY_INJECTION");
        assertThat(ex.getMessage()).isEqualTo("检测到 Prompt 注入");
    }

    @Test
    @DisplayName("构造异常时正确携带根因")
    void should_carryCause_when_constructedWithCause() {
        Throwable rootCause = new IllegalStateException("非法输入");

        SecurityViolationException ex = new SecurityViolationException("SECURITY_AUTH", "越权调用", rootCause);

        assertThat(ex.getCause()).isSameAs(rootCause);
        assertThat(ex.getErrorCode()).isEqualTo("SECURITY_AUTH");
    }

    @Test
    @DisplayName("ERROR_CODE_PREFIX 常量值为 SECURITY_")
    void should_haveSecurityPrefix_when_accessingConstant() {
        assertThat(SecurityViolationException.ERROR_CODE_PREFIX).isEqualTo("SECURITY_");
    }

    @Test
    @DisplayName("错误码为 null 时通过父类校验抛出 IllegalArgumentException")
    void should_throwIllegalArgument_when_errorCodeIsNull() {
        assertThatThrownBy(() -> new SecurityViolationException(null, "消息"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("错误码不能为空");
    }

    @Test
    @DisplayName("错误码为空白时通过父类校验抛出 IllegalArgumentException")
    void should_throwIllegalArgument_when_errorCodeIsBlank() {
        assertThatThrownBy(() -> new SecurityViolationException("  ", "消息"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("错误码不能为空");
    }

    @Test
    @DisplayName("带根因构造时错误码为空也抛出 IllegalArgumentException")
    void should_throwIllegalArgument_when_errorCodeIsBlankAndHasCause() {
        Throwable cause = new RuntimeException("root");

        assertThatThrownBy(() -> new SecurityViolationException("", "消息", cause))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("SecurityViolationException 是 AgentException 子类")
    void should_beAgentExceptionSubclass_when_checked() {
        SecurityViolationException ex = new SecurityViolationException("SECURITY_001", "违规");

        assertThat(ex).isInstanceOf(AgentException.class);
    }

    @Test
    @DisplayName("SecurityViolationException 是 RuntimeException 子类，可在运行时直接抛出")
    void should_beRuntimeException_when_checked() {
        SecurityViolationException ex = new SecurityViolationException("SECURITY_002", "违规");

        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
