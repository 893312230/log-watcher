package com.smartops.agent.router;

import com.smartops.agent.intent.IntentPipeline;
import com.smartops.agent.orchestrator.SupervisorAgent;
import com.smartops.agent.plan.PlanAndSolveExecutor;
import com.smartops.agent.react.ReActExecutor;
import com.smartops.common.enums.AgentMode;
import com.smartops.common.enums.IntentType;
import com.smartops.common.exception.LlmCallException;
import com.smartops.common.model.AgentExecutionResult;
import com.smartops.common.model.IntentResult;
import com.smartops.common.model.TaskComplexity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AgentRouter} 单元测试。
 *
 * <p>验证路由决策引擎的核心契约：
 * <ul>
 *   <li>根据 {@link TaskComplexity#suggestedMode()} 正确选择 ReAct 或 Plan-and-Solve 执行器</li>
 *   <li>执行器结果（成功/失败）透传</li>
 *   <li>意图识别异常与执行器异常时返回失败结果</li>
 *   <li>{@link AgentRouter#getRoutingDecision(String)} 返回正确的决策信息</li>
 *   <li>输入校验（null/空白）抛出 {@link IllegalArgumentException}</li>
 * </ul></p>
 *
 * <p>对应 agent.md 阶段二特性8（路由决策引擎）。</p>
 *
 * <p><b>测试策略</b>：
 * <ul>
 *   <li>使用 Mockito mock {@link IntentPipeline}、{@link TaskAnalyzer}、
 *       {@link ReActExecutor}、{@link PlanAndSolveExecutor}，隔离所有依赖</li>
 *   <li>使用 AssertJ 断言，遵循 Arrange-Act-Assert 三段式</li>
 *   <li>使用 {@code @Nested} 分组，{@code @DisplayName} 中文描述</li>
 *   <li>测试方法命名 {@code should_{期望行为}_when_{前置条件}}</li>
 * </ul></p>
 *
 * @author smartops
 * @since 1.0.0
 */
class AgentRouterTest {

    private IntentPipeline intentPipeline;
    private TaskAnalyzer taskAnalyzer;
    private ReActExecutor reactExecutor;
    private PlanAndSolveExecutor planAndSolveExecutor;
    private SupervisorAgent supervisorAgent;
    private AgentRouter router;

    @BeforeEach
    void setUp() {
        intentPipeline = mock(IntentPipeline.class);
        taskAnalyzer = mock(TaskAnalyzer.class);
        reactExecutor = mock(ReActExecutor.class);
        planAndSolveExecutor = mock(PlanAndSolveExecutor.class);
        supervisorAgent = mock(SupervisorAgent.class);
        router = new AgentRouter(intentPipeline, taskAnalyzer, reactExecutor,
                planAndSolveExecutor, supervisorAgent);
    }

    /**
     * 构造查询指标意图（简单任务，REACT 模式）。
     *
     * @return 查询指标意图识别结果
     */
    private IntentResult queryMetricIntent() {
        return new IntentResult(IntentType.QUERY_METRIC, 0.9, IntentResult.SOURCE_L1_REGEX, null);
    }

    /**
     * 构造运维操作意图（复杂任务，PLAN_AND_SOLVE 模式）。
     *
     * @return 运维操作意图识别结果
     */
    private IntentResult executeOpIntent() {
        return new IntentResult(IntentType.EXECUTE_OPERATION, 0.9, IntentResult.SOURCE_L1_REGEX, null);
    }

    /**
     * 构造 REACT 模式的任务复杂度。
     *
     * @return 简单实时任务复杂度
     */
    private TaskComplexity reactComplexity() {
        return new TaskComplexity(1, false, true, false, AgentMode.REACT, List.of("查询并返回结果"));
    }

    /**
     * 构造 PLAN_AND_SOLVE 模式的任务复杂度。
     *
     * @return 多步骤依赖任务复杂度
     */
    private TaskComplexity planComplexity() {
        return new TaskComplexity(3, true, false, false, AgentMode.PLAN_AND_SOLVE,
                List.of("步骤1", "步骤2", "步骤3"));
    }

    /**
     * 构造成功的执行结果。
     *
     * @param mode 执行模式
     * @return 成功的执行结果
     */
    private AgentExecutionResult successResult(AgentMode mode) {
        return AgentExecutionResult.success("答案", mode, 1, List.of("执行步骤"));
    }

    @Nested
    @DisplayName("路由选择")
    class RoutingSelection {

        @Test
        @DisplayName("suggestedMode=REACT 时调用 ReActExecutor")
        void should_callReActExecutor_when_suggestedModeIsReact() {
            // Arrange
            String userInput = "查询 CPU 使用率";
            String conversationId = "conv-001";
            IntentResult intent = queryMetricIntent();
            TaskComplexity complexity = reactComplexity();
            when(intentPipeline.recognize(anyString())).thenReturn(intent);
            when(taskAnalyzer.analyze(anyString(), eq(intent))).thenReturn(complexity);
            when(reactExecutor.execute(anyString(), anyString())).thenReturn(successResult(AgentMode.REACT));

            // Act
            AgentExecutionResult result = router.route(userInput, conversationId);

            // Assert
            assertThat(result.success()).isTrue();
            verify(reactExecutor).execute(eq(userInput), eq(conversationId));
        }

        @Test
        @DisplayName("suggestedMode=PLAN_AND_SOLVE 时调用 PlanAndSolveExecutor")
        void should_callPlanAndSolveExecutor_when_suggestedModeIsPlanAndSolve() {
            // Arrange
            String userInput = "重启服务然后清理缓存然后验证";
            String conversationId = "conv-002";
            IntentResult intent = executeOpIntent();
            TaskComplexity complexity = planComplexity();
            when(intentPipeline.recognize(anyString())).thenReturn(intent);
            when(taskAnalyzer.analyze(anyString(), eq(intent))).thenReturn(complexity);
            when(planAndSolveExecutor.execute(anyString(), anyString()))
                    .thenReturn(successResult(AgentMode.PLAN_AND_SOLVE));

            // Act
            AgentExecutionResult result = router.route(userInput, conversationId);

            // Assert
            assertThat(result.success()).isTrue();
            verify(planAndSolveExecutor).execute(eq(userInput), eq(conversationId));
        }

        @Test
        @DisplayName("REACT 模式下不调用 PlanAndSolveExecutor")
        void should_notCallPlanAndSolveExecutor_when_modeIsReact() {
            // Arrange
            IntentResult intent = queryMetricIntent();
            TaskComplexity complexity = reactComplexity();
            when(intentPipeline.recognize(anyString())).thenReturn(intent);
            when(taskAnalyzer.analyze(anyString(), eq(intent))).thenReturn(complexity);
            when(reactExecutor.execute(anyString(), anyString())).thenReturn(successResult(AgentMode.REACT));

            // Act
            router.route("查询 CPU", "conv-003");

            // Assert
            verify(planAndSolveExecutor, never()).execute(anyString(), anyString());
        }

        @Test
        @DisplayName("PLAN_AND_SOLVE 模式下不调用 ReActExecutor")
        void should_notCallReActExecutor_when_modeIsPlanAndSolve() {
            // Arrange
            IntentResult intent = executeOpIntent();
            TaskComplexity complexity = planComplexity();
            when(intentPipeline.recognize(anyString())).thenReturn(intent);
            when(taskAnalyzer.analyze(anyString(), eq(intent))).thenReturn(complexity);
            when(planAndSolveExecutor.execute(anyString(), anyString()))
                    .thenReturn(successResult(AgentMode.PLAN_AND_SOLVE));

            // Act
            router.route("重启服务然后清理缓存", "conv-004");

            // Assert
            verify(reactExecutor, never()).execute(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Supervisor 多 Agent 路由")
    class SupervisorRouting {

        @Test
        @DisplayName("跨领域复杂任务路由到 SupervisorAgent")
        void should_callSupervisor_when_multiDomainComplexTask() {
            // Arrange: 包含监控+分析关键词，步骤数 >= 3
            String userInput = "分析告警指标并排查根因然后重启服务";
            String conversationId = "conv-sup-001";
            IntentResult intent = executeOpIntent();
            TaskComplexity complexity = planComplexity();
            when(intentPipeline.recognize(anyString())).thenReturn(intent);
            when(taskAnalyzer.analyze(anyString(), eq(intent))).thenReturn(complexity);
            AgentExecutionResult supervisorResult = AgentExecutionResult.success(
                    "Multi-Agent 协作结果", AgentMode.PLAN_AND_SOLVE, 3,
                    List.of("监控", "分析", "执行"));
            when(supervisorAgent.orchestrate(anyString(), anyString())).thenReturn(supervisorResult);

            // Act
            AgentExecutionResult result = router.route(userInput, conversationId);

            // Assert
            assertThat(result).isEqualTo(supervisorResult);
            verify(supervisorAgent).orchestrate(eq(userInput), eq(conversationId));
            verify(reactExecutor, never()).execute(anyString(), anyString());
            verify(planAndSolveExecutor, never()).execute(anyString(), anyString());
        }

        @Test
        @DisplayName("单领域任务不路由到 SupervisorAgent")
        void should_notCallSupervisor_when_singleDomainTask() {
            // Arrange: 仅包含执行关键词
            String userInput = "重启服务然后清理缓存然后验证";
            IntentResult intent = executeOpIntent();
            TaskComplexity complexity = planComplexity();
            when(intentPipeline.recognize(anyString())).thenReturn(intent);
            when(taskAnalyzer.analyze(anyString(), eq(intent))).thenReturn(complexity);
            when(planAndSolveExecutor.execute(anyString(), anyString()))
                    .thenReturn(successResult(AgentMode.PLAN_AND_SOLVE));

            // Act
            router.route(userInput, "conv-sup-002");

            // Assert
            verify(supervisorAgent, never()).orchestrate(anyString(), anyString());
        }

        @Test
        @DisplayName("步骤数不足时不路由到 SupervisorAgent")
        void should_notCallSupervisor_when_stepsLessThanThree() {
            // Arrange: 跨领域但步骤数 < 3
            String userInput = "查询CPU指标并分析";
            IntentResult intent = queryMetricIntent();
            TaskComplexity complexity = reactComplexity();
            when(intentPipeline.recognize(anyString())).thenReturn(intent);
            when(taskAnalyzer.analyze(anyString(), eq(intent))).thenReturn(complexity);
            when(reactExecutor.execute(anyString(), anyString())).thenReturn(successResult(AgentMode.REACT));

            // Act
            router.route(userInput, "conv-sup-003");

            // Assert
            verify(supervisorAgent, never()).orchestrate(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("执行结果")
    class ExecutionResults {

        @Test
        @DisplayName("正常执行返回成功结果")
        void should_returnSuccessResult_when_normalExecution() {
            // Arrange
            IntentResult intent = queryMetricIntent();
            TaskComplexity complexity = reactComplexity();
            AgentExecutionResult expected = AgentExecutionResult.success(
                    "CPU 使用率 45.2%", AgentMode.REACT, 1, List.of("ReAct 执行开始", "ReAct 执行完成"));
            when(intentPipeline.recognize(anyString())).thenReturn(intent);
            when(taskAnalyzer.analyze(anyString(), eq(intent))).thenReturn(complexity);
            when(reactExecutor.execute(anyString(), anyString())).thenReturn(expected);

            // Act
            AgentExecutionResult result = router.route("查询 CPU", "conv-005");

            // Assert
            assertThat(result).isEqualTo(expected);
            assertThat(result.success()).isTrue();
            assertThat(result.answer()).isEqualTo("CPU 使用率 45.2%");
            assertThat(result.mode()).isEqualTo(AgentMode.REACT);
            assertThat(result.iterations()).isEqualTo(1);
            assertThat(result.errorMessage()).isNull();
        }

        @Test
        @DisplayName("执行器返回失败结果时透传")
        void should_passThroughFailure_when_executorReturnsFailure() {
            // Arrange
            IntentResult intent = queryMetricIntent();
            TaskComplexity complexity = reactComplexity();
            AgentExecutionResult failure = AgentExecutionResult.failure(
                    AgentMode.REACT, 0, List.of("执行步骤"), "LLM 服务不可用");
            when(intentPipeline.recognize(anyString())).thenReturn(intent);
            when(taskAnalyzer.analyze(anyString(), eq(intent))).thenReturn(complexity);
            when(reactExecutor.execute(anyString(), anyString())).thenReturn(failure);

            // Act
            AgentExecutionResult result = router.route("查询 CPU", "conv-006");

            // Assert
            assertThat(result).isEqualTo(failure);
            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).isEqualTo("LLM 服务不可用");
            assertThat(result.mode()).isEqualTo(AgentMode.REACT);
        }

        @Test
        @DisplayName("意图识别异常时返回失败结果")
        void should_returnFailure_when_intentPipelineThrowsException() {
            // Arrange
            String errorMessage = "LLM 服务不可用";
            when(intentPipeline.recognize(anyString()))
                    .thenThrow(new LlmCallException(errorMessage));

            // Act
            AgentExecutionResult result = router.route("查询 CPU", "conv-007");

            // Assert
            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("意图识别失败");
            assertThat(result.errorMessage()).contains(errorMessage);
            assertThat(result.mode()).isEqualTo(AgentMode.REACT);
            assertThat(result.iterations()).isEqualTo(0);
            // 意图识别失败后不应调用任何执行器
            verify(reactExecutor, never()).execute(anyString(), anyString());
            verify(planAndSolveExecutor, never()).execute(anyString(), anyString());
        }

        @Test
        @DisplayName("执行器抛出异常时返回失败结果")
        void should_returnFailure_when_executorThrowsException() {
            // Arrange
            IntentResult intent = queryMetricIntent();
            TaskComplexity complexity = reactComplexity();
            String errorMessage = "执行器内部错误";
            when(intentPipeline.recognize(anyString())).thenReturn(intent);
            when(taskAnalyzer.analyze(anyString(), eq(intent))).thenReturn(complexity);
            when(reactExecutor.execute(anyString(), anyString()))
                    .thenThrow(new LlmCallException(errorMessage));

            // Act
            AgentExecutionResult result = router.route("查询 CPU", "conv-008");

            // Assert
            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("执行器执行失败");
            assertThat(result.errorMessage()).contains(errorMessage);
            assertThat(result.mode()).isEqualTo(AgentMode.REACT);
            assertThat(result.iterations()).isEqualTo(0);
        }

        @Test
        @DisplayName("执行器抛出安全违规异常时向上传播（不转为失败结果）")
        void should_propagateSecurityViolation_when_executorThrowsIt() {
            // Arrange
            IntentResult intent = queryMetricIntent();
            TaskComplexity complexity = reactComplexity();
            when(intentPipeline.recognize(anyString())).thenReturn(intent);
            when(taskAnalyzer.analyze(anyString(), eq(intent))).thenReturn(complexity);
            when(reactExecutor.execute(anyString(), anyString()))
                    .thenThrow(new com.smartops.common.exception.SecurityViolationException(
                            "SECURITY_CONFIRM_REQUIRED", "高风险操作需要人工确认"));

            // Act & Assert
            assertThatThrownBy(() -> router.route("重启订单服务", "conv-sec"))
                    .isInstanceOf(com.smartops.common.exception.SecurityViolationException.class)
                    .hasMessageContaining("高风险操作需要人工确认");
        }
    }

    @Nested
    @DisplayName("路由决策")
    class RoutingDecision {

        @Test
        @DisplayName("getRoutingDecision 返回正确的意图和复杂度信息")
        void should_returnCorrectIntentAndComplexity_when_getRoutingDecision() {
            // Arrange
            IntentResult intent = queryMetricIntent();
            TaskComplexity complexity = reactComplexity();
            when(intentPipeline.recognize(anyString())).thenReturn(intent);
            when(taskAnalyzer.analyze(anyString(), eq(intent))).thenReturn(complexity);

            // Act
            AgentRouter.RoutingDecision decision = router.getRoutingDecision("查询 CPU");

            // Assert
            assertThat(decision.intentResult()).isEqualTo(intent);
            assertThat(decision.complexity()).isEqualTo(complexity);
            assertThat(decision.intentResult().intentType()).isEqualTo(IntentType.QUERY_METRIC);
            assertThat(decision.intentResult().confidence()).isEqualTo(0.9);
            assertThat(decision.complexity().estimatedSteps()).isEqualTo(1);
            assertThat(decision.complexity().realTimeRequired()).isTrue();
        }

        @Test
        @DisplayName("getRoutingDecision 返回正确的 selectedMode")
        void should_returnCorrectSelectedMode_when_getRoutingDecision() {
            // Arrange
            IntentResult intent = executeOpIntent();
            TaskComplexity complexity = planComplexity();
            when(intentPipeline.recognize(anyString())).thenReturn(intent);
            when(taskAnalyzer.analyze(anyString(), eq(intent))).thenReturn(complexity);

            // Act
            AgentRouter.RoutingDecision decision = router.getRoutingDecision("重启服务然后清理缓存");

            // Assert
            assertThat(decision.selectedMode()).isEqualTo(AgentMode.PLAN_AND_SOLVE);
            assertThat(decision.complexity().suggestedMode()).isEqualTo(AgentMode.PLAN_AND_SOLVE);
            // 不应调用任何执行器（仅做决策，不执行）
            verify(reactExecutor, never()).execute(anyString(), anyString());
            verify(planAndSolveExecutor, never()).execute(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("异常处理")
    class ExceptionHandling {

        @Test
        @DisplayName("route 输入为 null 时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_routeInputNull() {
            assertThatThrownBy(() -> router.route(null, "conv-009"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("用户输入");
        }

        @Test
        @DisplayName("route 输入为空白时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_routeInputBlank() {
            assertThatThrownBy(() -> router.route("   ", "conv-010"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("用户输入");
        }

        @Test
        @DisplayName("getRoutingDecision 输入为 null 时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_getRoutingDecisionInputNull() {
            assertThatThrownBy(() -> router.getRoutingDecision(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("用户输入");
        }

        @Test
        @DisplayName("getRoutingDecision 输入为空白时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_getRoutingDecisionInputBlank() {
            assertThatThrownBy(() -> router.getRoutingDecision("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("用户输入");
        }
    }
}
