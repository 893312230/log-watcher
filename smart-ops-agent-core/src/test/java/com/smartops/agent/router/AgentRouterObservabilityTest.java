package com.smartops.agent.router;

import com.smartops.agent.intent.IntentPipeline;
import com.smartops.agent.orchestrator.SupervisorAgent;
import com.smartops.agent.plan.PlanAndSolveExecutor;
import com.smartops.agent.react.ReActExecutor;
import com.smartops.common.enums.AgentMode;
import com.smartops.common.enums.IntentType;
import com.smartops.common.exception.AgentException;
import com.smartops.common.exception.SecurityViolationException;
import com.smartops.common.model.AgentExecutionResult;
import com.smartops.common.model.IntentResult;
import com.smartops.common.model.TaskComplexity;
import com.smartops.infrastructure.observability.Observability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AgentRouter} 任务执行观测钩子测试。
 *
 * <p>验证 route 边界在成功/失败/安全拦截/意图失败路径均记录
 * task.executions 指标与 TASK_EXECUTION 审计事件，
 * 旧构造器（无 observability）不影响路由行为。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class AgentRouterObservabilityTest {

    private IntentPipeline intentPipeline;
    private TaskAnalyzer taskAnalyzer;
    private ReActExecutor reactExecutor;
    private PlanAndSolveExecutor planAndSolveExecutor;
    private SupervisorAgent supervisorAgent;
    private Observability observability;
    private AgentRouter router;

    @BeforeEach
    void setUp() {
        intentPipeline = mock(IntentPipeline.class);
        taskAnalyzer = mock(TaskAnalyzer.class);
        reactExecutor = mock(ReActExecutor.class);
        planAndSolveExecutor = mock(PlanAndSolveExecutor.class);
        supervisorAgent = mock(SupervisorAgent.class);
        observability = mock(Observability.class);
        router = new AgentRouter(intentPipeline, taskAnalyzer, reactExecutor,
                planAndSolveExecutor, supervisorAgent, observability);
    }

    private void stubIntentAndComplexity(AgentMode mode) {
        when(intentPipeline.recognize(anyString())).thenReturn(
                new IntentResult(IntentType.QUERY_METRIC, 0.9, IntentResult.SOURCE_L1_REGEX, null));
        when(taskAnalyzer.analyze(anyString(), any())).thenReturn(
                new TaskComplexity(1, false, true, false, mode, List.of("步骤")));
    }

    @Test
    @DisplayName("ReAct 执行成功记录成功观测，模式为 REACT")
    void should_recordSuccess_when_reactSucceeds() {
        stubIntentAndComplexity(AgentMode.REACT);
        when(reactExecutor.execute(anyString(), anyString())).thenReturn(
                AgentExecutionResult.success("答案", AgentMode.REACT, 3, List.of()));

        router.route("查询 CPU", "conv-1");

        verify(observability).recordTaskExecution(eq("REACT"), eq("conv-1"), eq(true),
                anyLong(), eq("迭代次数: 3"));
    }

    @Test
    @DisplayName("Supervisor 路径记录模式为 SUPERVISOR")
    void should_recordSupervisorMode_when_supervisorPath() {
        stubIntentAndComplexity(AgentMode.PLAN_AND_SOLVE);
        when(taskAnalyzer.analyze(anyString(), any())).thenReturn(
                new TaskComplexity(3, true, false, false, AgentMode.PLAN_AND_SOLVE,
                        List.of("a", "b", "c")));
        when(supervisorAgent.orchestrate(anyString(), anyString())).thenReturn(
                AgentExecutionResult.success("综合答案", AgentMode.REACT, 2, List.of()));

        router.route("监控指标并分析根因并重启服务", "conv-2");

        verify(observability).recordTaskExecution(eq("SUPERVISOR"), eq("conv-2"), eq(true),
                anyLong(), anyString());
    }

    @Test
    @DisplayName("执行器返回失败结果时记录失败观测")
    void should_recordFailure_when_resultFailure() {
        stubIntentAndComplexity(AgentMode.REACT);
        when(reactExecutor.execute(anyString(), anyString())).thenReturn(
                AgentExecutionResult.failure(AgentMode.REACT, 1, List.of(), "LLM 超时"));

        router.route("查询 CPU", "conv-3");

        verify(observability).recordTaskExecution(eq("REACT"), eq("conv-3"), eq(false),
                anyLong(), eq("LLM 超时"));
    }

    @Test
    @DisplayName("执行器抛平台异常时记录失败观测")
    void should_recordFailure_when_agentException() {
        stubIntentAndComplexity(AgentMode.REACT);
        when(reactExecutor.execute(anyString(), anyString()))
                .thenThrow(new AgentException("EXEC_ERROR", "工具异常"));

        router.route("查询 CPU", "conv-4");

        verify(observability).recordTaskExecution(eq("REACT"), eq("conv-4"), eq(false),
                anyLong(), contains("工具异常"));
    }

    @Test
    @DisplayName("安全违规抛出时记录失败观测并重抛")
    void should_recordFailureAndRethrow_when_securityViolation() {
        stubIntentAndComplexity(AgentMode.REACT);
        when(reactExecutor.execute(anyString(), anyString()))
                .thenThrow(new SecurityViolationException("SEC", "需要确认"));

        try {
            router.route("重启服务", "conv-5");
        } catch (SecurityViolationException ignored) {
            // 期望抛出
        }

        verify(observability).recordTaskExecution(eq("REACT"), eq("conv-5"), eq(false),
                anyLong(), contains("安全确认待处理"));
    }

    @Test
    @DisplayName("意图识别失败时记录失败观测")
    void should_recordFailure_when_intentFails() {
        when(intentPipeline.recognize(anyString()))
                .thenThrow(new AgentException("INTENT_ERROR", "识别异常"));

        router.route("任意输入", "conv-6");

        verify(observability).recordTaskExecution(eq("REACT"), eq("conv-6"), eq(false),
                anyLong(), contains("意图识别失败"));
    }

    @Test
    @DisplayName("旧构造器（无 observability）路由正常且不记录")
    void should_workWithoutObservability_when_fiveArgConstructor() {
        AgentRouter plain = new AgentRouter(intentPipeline, taskAnalyzer, reactExecutor,
                planAndSolveExecutor, supervisorAgent);
        stubIntentAndComplexity(AgentMode.REACT);
        when(reactExecutor.execute(anyString(), anyString())).thenReturn(
                AgentExecutionResult.success("答案", AgentMode.REACT, 1, List.of()));

        plain.route("查询 CPU", "conv-7");

        verify(observability, never()).recordTaskExecution(anyString(), anyString(),
                any(Boolean.class), anyLong(), any());
    }
}
