package com.smartops.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LogWatchException} 单元测试。
 *
 * <p>验证错误码固定、消息与根因传递、异常分层继承关系。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class LogWatchExceptionTest {

    @Test
    @DisplayName("单参构造固定错误码并携带消息")
    void should_carryFixedErrorCodeAndMessage_when_constructedWithMessage() {
        LogWatchException ex = new LogWatchException("日志采集失败");

        assertThat(ex.getErrorCode()).isEqualTo(LogWatchException.ERROR_CODE);
        assertThat(ex.getMessage()).isEqualTo("日志采集失败");
    }

    @Test
    @DisplayName("带根因构造正确关联原始异常")
    void should_carryCause_when_constructedWithCause() {
        Throwable rootCause = new IllegalStateException("文件不可读");

        LogWatchException ex = new LogWatchException("tail 日志文件失败", rootCause);

        assertThat(ex.getCause()).isSameAs(rootCause);
        assertThat(ex.getErrorCode()).isEqualTo("LOG_WATCH_FAILED");
    }

    @Test
    @DisplayName("LogWatchException 是 AgentException 子类，可被类型化捕获")
    void should_beAgentExceptionSubclass_when_checked() {
        LogWatchException ex = new LogWatchException("失败");

        assertThat(ex).isInstanceOf(AgentException.class);
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
