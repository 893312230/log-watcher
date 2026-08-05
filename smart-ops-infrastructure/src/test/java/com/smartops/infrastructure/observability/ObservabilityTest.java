package com.smartops.infrastructure.observability;

import com.smartops.common.enums.AuditEventType;
import com.smartops.domain.audit.AuditEvent;
import com.smartops.domain.audit.port.AuditRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

/**
 * {@link Observability} 单元测试。
 *
 * <p>覆盖：四类事件的指标+审计双通道记录、依赖缺失静默跳过。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class ObservabilityTest {

    private static <T> ObjectProvider<T> providerOf(T instance) {
        return new ObjectProvider<>() {
            @Override
            public T getObject() {
                return instance;
            }

            @Override
            public T getIfAvailable() {
                return instance;
            }
        };
    }

    private static <T> ObjectProvider<T> emptyProvider() {
        return providerOf(null);
    }

    private List<AuditEvent> recorded;
    private SimpleMeterRegistry registry;
    private Observability observability;

    private void setUp(boolean withRegistry, boolean withRecorder) {
        recorded = new ArrayList<>();
        registry = new SimpleMeterRegistry();
        AuditRecorder recorder = recorded::add;
        observability = new Observability(
                withRegistry ? providerOf(registry) : emptyProvider(),
                withRecorder ? providerOf(recorder) : emptyProvider());
    }

    @Test
    @DisplayName("LLM 调用记录指标与 LLM_CALL 审计事件")
    void should_recordMetricAndAudit_when_llmCall() {
        setUp(true, true);

        observability.recordLlmCall(true, 120, "回复摘要");

        assertThat(registry.get(Observability.METRIC_LLM_CALLS)
                .tags("success", "true").timer().count()).isEqualTo(1);
        assertThat(registry.get(Observability.METRIC_LLM_CALLS)
                .tags("success", "true").timer()
                .totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(120.0);
        assertThat(recorded).hasSize(1);
        AuditEvent event = recorded.get(0);
        assertThat(event.eventType()).isEqualTo(AuditEventType.LLM_CALL);
        assertThat(event.actor()).isEqualTo("chatService");
        assertThat(event.success()).isTrue();
        assertThat(event.latencyMs()).isEqualTo(120);
        assertThat(event.detail()).isEqualTo("回复摘要");
    }

    @Test
    @DisplayName("工具调用记录带工具名标签的指标与 TOOL_CALL 审计事件")
    void should_recordToolCall_when_invoked() {
        setUp(true, true);

        observability.recordToolCall("queryMetric", false, 30, "参数错误");

        assertThat(registry.get(Observability.METRIC_TOOL_CALLS)
                .tags("tool", "queryMetric", "success", "false").timer().count()).isEqualTo(1);
        assertThat(recorded.get(0).eventType()).isEqualTo(AuditEventType.TOOL_CALL);
        assertThat(recorded.get(0).actor()).isEqualTo("queryMetric");
        assertThat(recorded.get(0).success()).isFalse();
    }

    @Test
    @DisplayName("任务执行记录带模式标签的指标与 TASK_EXECUTION 审计事件")
    void should_recordTaskExecution_when_invoked() {
        setUp(true, true);

        observability.recordTaskExecution("SUPERVISOR", "conv-9", true, 5000, "3 个子任务");

        assertThat(registry.get(Observability.METRIC_TASK_EXECUTIONS)
                .tags("mode", "SUPERVISOR", "success", "true").timer().count()).isEqualTo(1);
        AuditEvent event = recorded.get(0);
        assertThat(event.eventType()).isEqualTo(AuditEventType.TASK_EXECUTION);
        assertThat(event.traceId()).isEqualTo("conv-9");
        assertThat(event.target()).isEqualTo("SUPERVISOR");
    }

    @Test
    @DisplayName("安全决策记录指标与 SECURITY_DECISION 审计事件")
    void should_recordSecurityDecision_when_invoked() {
        setUp(true, true);

        observability.recordSecurityDecision("重启服务 order-service", false);

        assertThat(registry.get(Observability.METRIC_SECURITY_DECISIONS)
                .tags("permitted", "false").timer().count()).isEqualTo(1);
        AuditEvent event = recorded.get(0);
        assertThat(event.eventType()).isEqualTo(AuditEventType.SECURITY_DECISION);
        assertThat(event.success()).isFalse();
    }

    @Test
    @DisplayName("依赖缺失时静默跳过，不抛异常")
    void should_silentlySkip_when_dependenciesMissing() {
        setUp(false, false);

        assertThatCode(() -> {
            observability.recordLlmCall(true, 1, "x");
            observability.recordToolCall("t", true, 1, "x");
            observability.recordTaskExecution("REACT", null, true, 1, "x");
            observability.recordSecurityDecision("op", true);
        }).doesNotThrowAnyException();

        assertThat(recorded).isEmpty();
    }


    @Test
    @DisplayName("仅有审计记录器时审计照常记录")
    void should_recordAuditOnly_when_registryMissing() {
        setUp(false, true);

        observability.recordLlmCall(true, 1, "x");

        assertThat(recorded).hasSize(1);
    }

    @Test
    @DisplayName("已认证用户上下文中 actor 追加 @用户名")
    void should_appendUsername_when_authenticated() {
        setUp(false, true);
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "admin", null, java.util.List.of(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            observability.recordLlmCall(true, 1, "x");
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }

        assertThat(recorded.get(0).actor()).isEqualTo("chatService@admin");
    }

    @Test
    @DisplayName("认证信息读取异常时 actor 保持原值")
    void should_keepActor_when_contextReadFails() {
        setUp(false, true);
        var auth = org.mockito.Mockito.mock(
                org.springframework.security.core.Authentication.class);
        org.mockito.Mockito.when(auth.isAuthenticated()).thenReturn(true);
        org.mockito.Mockito.when(auth.getPrincipal()).thenReturn("admin");
        org.mockito.Mockito.when(auth.getName()).thenThrow(new RuntimeException("broken"));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            observability.recordLlmCall(true, 1, "x");
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }

        assertThat(recorded.get(0).actor()).isEqualTo("chatService");
    }

    @Test
    @DisplayName("匿名认证不追加用户名")
    void should_keepActor_when_anonymous() {
        setUp(false, true);
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "anonymousUser", null, java.util.List.of());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            observability.recordLlmCall(true, 1, "x");
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }

        assertThat(recorded.get(0).actor()).isEqualTo("chatService");
    }
}
