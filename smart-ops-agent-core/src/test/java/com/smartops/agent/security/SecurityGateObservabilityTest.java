package com.smartops.agent.security;

import com.smartops.common.exception.SecurityViolationException;
import com.smartops.infrastructure.observability.Observability;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link SecurityGate} 安全决策观测钩子测试。
 *
 * <p>验证高危操作的放行/拦截均记录 security.decisions 指标与
 * SECURITY_DECISION 审计事件，低危操作不记录（避免噪音），
 * 旧构造器（无 observability）不影响门行为。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class SecurityGateObservabilityTest {

    private final Observability observability = mock(Observability.class);
    private final SecurityGate gate = new SecurityGate(observability);

    @AfterEach
    void tearDown() {
        ConfirmationContext.clear();
    }

    @Test
    @DisplayName("高危操作经确认放行时记录 permitted=true 观测")
    void should_recordPermitted_when_highRiskConfirmed() {
        ConfirmationContext.markConfirmed();

        gate.checkPermitted("重启服务 order-service");

        verify(observability).recordSecurityDecision(contains("重启服务"), eq(true));
    }

    @Test
    @DisplayName("高危操作未确认拦截时记录 permitted=false 观测并抛异常")
    void should_recordDenied_when_highRiskNotConfirmed() {
        assertThatThrownBy(() -> gate.checkPermitted("重启服务 order-service"))
                .isInstanceOf(SecurityViolationException.class);

        verify(observability).recordSecurityDecision(contains("重启服务"), eq(false));
    }

    @Test
    @DisplayName("低危操作直接放行且不记录观测")
    void should_notRecord_when_lowRisk() {
        assertThatCode(() -> gate.checkPermitted("查询 CPU 使用率")).doesNotThrowAnyException();

        verify(observability, never()).recordSecurityDecision(anyString(), any(Boolean.class));
    }

    @Test
    @DisplayName("旧构造器（无 observability）门行为不变")
    void should_workWithoutObservability_when_noArgConstructor() {
        SecurityGate plain = new SecurityGate();

        assertThatCode(() -> plain.checkPermitted("查询 CPU 使用率")).doesNotThrowAnyException();
        assertThatThrownBy(() -> plain.checkPermitted("重启服务"))
                .isInstanceOf(SecurityViolationException.class);

        verify(observability, never()).recordSecurityDecision(anyString(), any(Boolean.class));
    }
}
