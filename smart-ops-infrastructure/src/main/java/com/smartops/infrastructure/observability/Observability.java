package com.smartops.infrastructure.observability;

import com.smartops.common.enums.AuditEventType;
import com.smartops.domain.audit.AuditEvent;
import com.smartops.domain.audit.port.AuditRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * 可观测性门面（阶段五：Micrometer 指标 + L2 操作审计统一出口）。
 *
 * <p>各观测钩子（ChatService/ToolCallingManager/AgentRouter/SecurityGate）
 * 经本组件同时记录指标与审计事件，避免每个钩子重复处理可空依赖。
 * 依赖缺失（MeterRegistry/AuditRecorder 无 Bean）时对应通道静默跳过，
 * 任何情况下不向业务链路抛异常。</p>
 *
 * <p>线程安全：Micrometer 与 AuditRecorder 均线程安全，本类无状态。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Component
public class Observability {

    /** LLM 调用指标名（tags: success）。 */
    public static final String METRIC_LLM_CALLS = "smartops.llm.calls";

    /** 工具调用指标名（tags: tool, success）。 */
    public static final String METRIC_TOOL_CALLS = "smartops.tool.calls";

    /** 任务执行指标名（tags: mode, success）。 */
    public static final String METRIC_TASK_EXECUTIONS = "smartops.task.executions";

    /** 安全决策指标名（tags: permitted）。 */
    public static final String METRIC_SECURITY_DECISIONS = "smartops.security.decisions";

    private final MeterRegistry meterRegistry;
    private final AuditRecorder auditRecorder;

    /**
     * 构造可观测性门面。
     *
     * @param meterRegistryProvider 指标注册表提供者（无 Bean 时仅审计）
     * @param auditRecorderProvider 审计记录器提供者（无 Bean 时仅指标）
     */
    public Observability(ObjectProvider<MeterRegistry> meterRegistryProvider,
                         ObjectProvider<AuditRecorder> auditRecorderProvider) {
        this.meterRegistry = meterRegistryProvider.getIfAvailable();
        this.auditRecorder = auditRecorderProvider.getIfAvailable();
    }

    /**
     * 记录一次 LLM 调用。
     *
     * @param success   是否成功
     * @param latencyMs 耗时毫秒
     * @param detail    摘要（响应或错误信息，超长自动截断）
     */
    public void recordLlmCall(boolean success, long latencyMs, String detail) {
        record(METRIC_LLM_CALLS, new String[]{"success", String.valueOf(success)},
                AuditEventType.LLM_CALL, null, "chatService", null,
                success, latencyMs, detail);
    }

    /**
     * 记录一次工具调用。
     *
     * @param tool      工具名
     * @param success   是否成功
     * @param latencyMs 耗时毫秒
     * @param detail    摘要（入参/结果截断）
     */
    public void recordToolCall(String tool, boolean success, long latencyMs, String detail) {
        record(METRIC_TOOL_CALLS,
                new String[]{"tool", tool, "success", String.valueOf(success)},
                AuditEventType.TOOL_CALL, null, tool, null,
                success, latencyMs, detail);
    }

    /**
     * 记录一次任务执行（AgentRouter 统一边界）。
     *
     * @param mode      执行模式（REACT/PLAN/SUPERVISOR 等）
     * @param traceId   会话 id
     * @param success   是否成功
     * @param latencyMs 耗时毫秒
     * @param detail    摘要（迭代步数/错误信息）
     */
    public void recordTaskExecution(String mode, String traceId, boolean success,
                                    long latencyMs, String detail) {
        record(METRIC_TASK_EXECUTIONS,
                new String[]{"mode", mode, "success", String.valueOf(success)},
                AuditEventType.TASK_EXECUTION, traceId, "agentRouter", mode,
                success, latencyMs, detail);
    }

    /**
     * 记录一次安全决策（SecurityGate 高风险操作放行/拦截）。
     *
     * @param operation 操作摘要
     * @param permitted 是否放行
     */
    public void recordSecurityDecision(String operation, boolean permitted) {
        record(METRIC_SECURITY_DECISIONS,
                new String[]{"permitted", String.valueOf(permitted)},
                AuditEventType.SECURITY_DECISION, null, "securityGate", null,
                permitted, 0, operation);
    }

    /**
     * 指标 + 审计双通道记录（任何通道失败不影响另一通道与业务）。
     */
    private void record(String metric, String[] tags, AuditEventType type, String traceId,
                        String actor, String target, boolean success,
                        long latencyMs, String detail) {
        if (meterRegistry != null) {
            try {
                Timer.builder(metric).tags(tags).register(meterRegistry)
                        .record(latencyMs, TimeUnit.MILLISECONDS);
            } catch (RuntimeException e) {
                // 指标失败静默：不得影响审计与业务
            }
        }
        if (auditRecorder != null) {
            auditRecorder.record(AuditEvent.create(type, traceId, resolveActor(actor), target,
                    detail, success, latencyMs, Instant.now()));
        }
    }

    /**
     * 解析审计 actor：存在已认证用户（阶段十二用户体系）时追加
     * {@code @用户名} 后缀；无会话上下文（后台线程/单元测试）时保持原值。
     */
    private String resolveActor(String actor) {
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()
                    || "anonymousUser".equals(auth.getPrincipal())) {
                return actor;
            }
            return actor + "@" + auth.getName();
        } catch (RuntimeException e) {
            return actor;
        }
    }
}
