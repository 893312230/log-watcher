package com.smartops.agent.security;

import com.smartops.common.exception.SecurityViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SecurityGate} 单元测试。
 *
 * <p>验证最小安全门的风险分级与放行/拦截契约：
 * 高危操作（重启/扩缩容/删除等）未经人工确认时抛出安全违规异常，
 * 已确认或只读操作直接放行。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class SecurityGateTest {

    private final SecurityGate gate = new SecurityGate();

    @AfterEach
    void tearDown() {
        // 防止线程确认标记跨用例泄露
        ConfirmationContext.clear();
    }

    @Nested
    @DisplayName("风险分级（assessRisk）")
    class RiskAssessment {

        @ParameterizedTest
        @ValueSource(strings = {"重启订单服务", "扩容到 4 副本", "删除命名空间", "执行 restart",
                "配置变更：调整超时时间", "部署新版本", "停机维护", "回滚发布", "scale up"})
        @DisplayName("变更类操作判定为 HIGH 风险")
        void should_assessHigh_when_mutatingOperation(String operation) {
            assertThat(gate.assessRisk(operation)).isEqualTo(SecurityGate.OperationRisk.HIGH);
        }

        @ParameterizedTest
        @ValueSource(strings = {"查询 CPU 使用率", "分析错误日志", "什么是熔断机制", "查看当前状态"})
        @DisplayName("只读操作判定为 LOW 风险")
        void should_assessLow_when_readOnlyOperation(String operation) {
            assertThat(gate.assessRisk(operation)).isEqualTo(SecurityGate.OperationRisk.LOW);
        }

        @Test
        @DisplayName("null 或空白文本判定为 LOW 风险")
        void should_assessLow_when_textNullOrBlank() {
            assertThat(gate.assessRisk(null)).isEqualTo(SecurityGate.OperationRisk.LOW);
            assertThat(gate.assessRisk("   ")).isEqualTo(SecurityGate.OperationRisk.LOW);
        }
    }

    @Nested
    @DisplayName("执行校验（checkPermitted）")
    class PermissionCheck {

        @Test
        @DisplayName("只读操作直接放行")
        void should_pass_when_lowRiskOperation() {
            assertThatCode(() -> gate.checkPermitted("查询 CPU 使用率"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("高危操作未经确认时抛出安全违规异常")
        void should_throwSecurityViolation_when_highRiskNotConfirmed() {
            assertThatThrownBy(() -> gate.checkPermitted("重启订单服务"))
                    .isInstanceOf(SecurityViolationException.class)
                    .hasMessageContaining("人工确认");
        }

        @Test
        @DisplayName("高危操作经人工确认后放行")
        void should_pass_when_highRiskConfirmed() {
            ConfirmationContext.markConfirmed();

            assertThatCode(() -> gate.checkPermitted("重启订单服务"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("安全违规异常的错误码为 SECURITY_CONFIRM_REQUIRED")
        void should_useConfirmRequiredErrorCode_when_rejected() {
            assertThatThrownBy(() -> gate.checkPermitted("删除数据库"))
                    .isInstanceOf(SecurityViolationException.class)
                    .extracting(e -> ((SecurityViolationException) e).getErrorCode())
                    .isEqualTo(SecurityGate.ERROR_CODE_CONFIRM_REQUIRED);
        }

        @Test
        @DisplayName("超长指令的异常消息被截断")
        void should_truncateLongOperation_when_rejected() {
            String longOperation = "重启" + "非常长的服务名称".repeat(20);

            assertThatThrownBy(() -> gate.checkPermitted(longOperation))
                    .isInstanceOf(SecurityViolationException.class)
                    .hasMessageContaining("...");
        }
    }

    @Nested
    @DisplayName("确认上下文（ConfirmationContext）")
    class ContextBehavior {

        @Test
        @DisplayName("默认未确认")
        void should_notConfirmed_when_freshThread() {
            assertThat(ConfirmationContext.isConfirmed()).isFalse();
        }

        @Test
        @DisplayName("清除后恢复未确认状态")
        void should_resetConfirmed_when_cleared() {
            ConfirmationContext.markConfirmed();
            assertThat(ConfirmationContext.isConfirmed()).isTrue();

            ConfirmationContext.clear();

            assertThat(ConfirmationContext.isConfirmed()).isFalse();
        }
    }
}
