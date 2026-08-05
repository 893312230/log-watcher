package com.smartops.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LlmCallException} 单元测试。
 *
 * <p>验证错误码固定、消息与根因传递、异常分层继承关系。
 * 对应 agent.md 第五章 5.3 节异常分层规范。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class LlmCallExceptionTest {

    @Test
    @DisplayName("单参构造固定错误码并携带消息")
    void should_carryFixedErrorCodeAndMessage_when_constructedWithMessage() {
        LlmCallException ex = new LlmCallException("LLM 服务不可用");

        assertThat(ex.getErrorCode()).isEqualTo(LlmCallException.ERROR_CODE);
        assertThat(ex.getMessage()).isEqualTo("LLM 服务不可用");
    }

    @Test
    @DisplayName("带根因构造正确关联原始异常")
    void should_carryCause_when_constructedWithCause() {
        Throwable rootCause = new IllegalStateException("HTTP 503");

        LlmCallException ex = new LlmCallException("LLM 调用失败", rootCause);

        assertThat(ex.getCause()).isSameAs(rootCause);
        assertThat(ex.getErrorCode()).isEqualTo("LLM_CALL_FAILED");
    }

    @Test
    @DisplayName("LlmCallException 是 AgentException 子类，可被类型化捕获")
    void should_beAgentExceptionSubclass_when_checked() {
        LlmCallException ex = new LlmCallException("失败");

        assertThat(ex).isInstanceOf(AgentException.class);
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
