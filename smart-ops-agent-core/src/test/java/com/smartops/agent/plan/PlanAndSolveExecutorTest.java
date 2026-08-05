package com.smartops.agent.plan;

import com.smartops.agent.tools.PrometheusTools;
import com.smartops.common.enums.AgentMode;
import com.smartops.common.exception.LlmCallException;
import com.smartops.common.model.AgentExecutionResult;
import com.smartops.common.model.ExecutionPlan;
import com.smartops.common.model.ExecutionPlan.PlanStep;
import com.smartops.infrastructure.chat.ChatService;
import com.smartops.infrastructure.memory.WorkingMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link PlanAndSolveExecutor} 单元测试。
 *
 * <p>验证 Plan-and-Solve 执行器的计划编排、带工具的步骤执行、
 * 续接式重新规划（只重建剩余步骤，上限 2 次）、总结生成、异常处理。
 * 对应 agent.md 阶段二任务8 的核心编排组件。</p>
 *
 * <p><b>测试要点</b>：
 * <ul>
 *   <li>PlanGenerator、ChatService、PrometheusTools 均 Mock，隔离 LLM 与工具依赖</li>
 *   <li>步骤执行必须走 chatWithTools 并携带 PrometheusTools（断言行为）</li>
 *   <li>步骤失败时调用 PlanGenerator.replan 只重建剩余步骤，已成功步骤不重复执行</li>
 *   <li>重新规划上限 2 次，超过返回失败</li>
 *   <li>所有结果（成功/失败）的 mode 必须为 PLAN_AND_SOLVE</li>
 * </ul></p>
 *
 * @author smartops
 * @since 1.0.0
 */
class PlanAndSolveExecutorTest {

    private PlanGenerator planGenerator;
    private ChatService chatService;
    private PrometheusTools prometheusTools;
    private ObjectProvider<WorkingMemory> workingMemoryProvider;
    private PlanAndSolveExecutor executor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        planGenerator = mock(PlanGenerator.class);
        chatService = mock(ChatService.class);
        prometheusTools = mock(PrometheusTools.class);
        // 默认 getIfAvailable() 返回 null：全部既有用例同时覆盖"工作记忆未启用"降级分支
        workingMemoryProvider = mock(ObjectProvider.class);
        executor = new PlanAndSolveExecutor(planGenerator, chatService, prometheusTools,
                workingMemoryProvider);
    }

    /**
     * 构造测试用执行计划。
     *
     * @param stepDescriptions 步骤描述列表（动作自动生成为 "动作N"）
     * @return 执行计划
     */
    private ExecutionPlan createPlan(String... stepDescriptions) {
        List<PlanStep> steps = new ArrayList<>();
        for (int i = 0; i < stepDescriptions.length; i++) {
            steps.add(PlanStep.of(i, stepDescriptions[i], "动作" + (i + 1)));
        }
        return ExecutionPlan.of("测试目标", steps);
    }

    @Nested
    @DisplayName("正常执行")
    class NormalExecution {

        @Test
        @DisplayName("正常执行多步骤计划返回成功结果，iterations 为成功步骤数")
        void should_returnSuccess_when_multiStepPlanSucceeds() {
            ExecutionPlan plan = createPlan("查询指标", "分析结果", "生成报告");
            when(planGenerator.generate(anyString())).thenReturn(plan);
            when(chatService.chatWithTools(anyString(), anyString(), any())).thenReturn("步骤结果");
            when(chatService.chatWithSystemPrompt(anyString(), anyString())).thenReturn("最终总结");

            AgentExecutionResult result = executor.execute("分析CPU情况", "conv-1");

            assertThat(result.success()).isTrue();
            assertThat(result.answer()).isEqualTo("最终总结");
            assertThat(result.mode()).isEqualTo(AgentMode.PLAN_AND_SOLVE);
            assertThat(result.iterations()).isEqualTo(3);
            assertThat(result.steps()).hasSize(3);
            assertThat(result.errorMessage()).isNull();
        }

        @Test
        @DisplayName("步骤执行携带会话 ID、步骤动作与 PrometheusTools")
        void should_executeStepsWithTools_when_running() {
            ExecutionPlan plan = createPlan("查询指标");
            when(planGenerator.generate(anyString())).thenReturn(plan);
            when(chatService.chatWithTools(anyString(), anyString(), any())).thenReturn("查询结果");
            when(chatService.chatWithSystemPrompt(anyString(), anyString())).thenReturn("总结");

            executor.execute("查询CPU", "conv-2");

            verify(chatService).chatWithTools(eq("conv-2"), eq("动作1"), eq(prometheusTools));
        }

        @Test
        @DisplayName("返回结果包含正确的 AgentMode.PLAN_AND_SOLVE")
        void should_containPlanAndSolveMode_when_executed() {
            ExecutionPlan plan = createPlan("步骤一");
            when(planGenerator.generate(anyString())).thenReturn(plan);
            when(chatService.chatWithTools(anyString(), anyString(), any())).thenReturn("结果");
            when(chatService.chatWithSystemPrompt(anyString(), anyString())).thenReturn("总结");

            AgentExecutionResult result = executor.execute("测试", "conv-3");

            assertThat(result.mode()).isEqualTo(AgentMode.PLAN_AND_SOLVE);
        }

        @Test
        @DisplayName("步骤记录包含每个步骤的执行结果")
        void should_recordStepResults_when_stepsSucceed() {
            ExecutionPlan plan = createPlan("查询", "分析");
            when(planGenerator.generate(anyString())).thenReturn(plan);
            when(chatService.chatWithTools(anyString(), anyString(), any()))
                    .thenReturn("查询结果", "分析结果");
            when(chatService.chatWithSystemPrompt(anyString(), anyString())).thenReturn("总结");

            AgentExecutionResult result = executor.execute("测试", "conv-4");

            assertThat(result.steps()).hasSize(2);
            assertThat(result.steps().get(0)).contains("查询").contains("查询结果");
            assertThat(result.steps().get(1)).contains("分析").contains("分析结果");
        }

        @Test
        @DisplayName("步骤执行返回 null 时仍视为成功")
        void should_succeed_when_stepResultIsNull() {
            ExecutionPlan plan = createPlan("步骤");
            when(planGenerator.generate(anyString())).thenReturn(plan);
            when(chatService.chatWithTools(anyString(), anyString(), any())).thenReturn(null);
            when(chatService.chatWithSystemPrompt(anyString(), anyString())).thenReturn("总结");

            AgentExecutionResult result = executor.execute("测试", "conv-4b");

            assertThat(result.success()).isTrue();
            assertThat(result.steps()).hasSize(1);
            assertThat(result.steps().get(0)).contains("步骤");
        }
    }

    @Nested
    @DisplayName("续接式重新规划")
    class ContinuationReplan {

        @Test
        @DisplayName("步骤失败时调用 replan 只重建剩余步骤并成功续接")
        void should_replanRemainingAndSucceed_when_stepFails() {
            ExecutionPlan failPlan = createPlan("失败步骤");
            ExecutionPlan remainingPlan = createPlan("补救步骤");
            when(planGenerator.generate(anyString())).thenReturn(failPlan);
            when(planGenerator.replan(anyString(), anyList(), anyString())).thenReturn(remainingPlan);
            when(chatService.chatWithTools(anyString(), anyString(), any()))
                    .thenThrow(new LlmCallException("步骤执行失败"))
                    .thenReturn("补救结果");
            when(chatService.chatWithSystemPrompt(anyString(), anyString())).thenReturn("最终总结");

            AgentExecutionResult result = executor.execute("测试", "conv-7");

            assertThat(result.success()).isTrue();
            assertThat(result.answer()).isEqualTo("最终总结");
            assertThat(result.iterations()).isEqualTo(1);
            // 初始计划 1 次 + 重新规划 1 次，且走 replan 而非 generate
            verify(planGenerator, times(1)).generate(anyString());
            verify(planGenerator, times(1)).replan(eq("测试"), anyList(), eq("步骤执行失败"));
        }

        @Test
        @DisplayName("第二步失败时已成功的第一步不重复执行")
        void should_notReexecuteSucceededSteps_when_secondStepFails() {
            ExecutionPlan failPlan = createPlan("步骤一", "步骤二");
            // 剩余计划使用与初始计划不同的动作名，便于断言执行序列
            ExecutionPlan remainingPlan = ExecutionPlan.of("测试目标",
                    List.of(PlanStep.of(0, "剩余步骤", "补救动作")));
            when(planGenerator.generate(anyString())).thenReturn(failPlan);
            when(planGenerator.replan(anyString(), anyList(), anyString())).thenReturn(remainingPlan);
            when(chatService.chatWithTools(anyString(), anyString(), any()))
                    .thenReturn("结果一")
                    .thenThrow(new LlmCallException("第二步失败"))
                    .thenReturn("续接结果");
            when(chatService.chatWithSystemPrompt(anyString(), anyString())).thenReturn("总结");

            AgentExecutionResult result = executor.execute("测试", "conv-12");

            assertThat(result.success()).isTrue();
            // 执行序列：第一步成功 → 第二步失败 → 只执行补救动作，第一步不重复
            ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
            verify(chatService, times(3)).chatWithTools(anyString(), actionCaptor.capture(), any());
            assertThat(actionCaptor.getAllValues())
                    .containsExactly("动作1", "动作2", "补救动作");
            assertThat(result.iterations()).isEqualTo(2);
        }

        @Test
        @DisplayName("重新规划超过上限 2 次返回失败结果")
        void should_returnFailure_when_replanExceedsLimit() {
            ExecutionPlan plan = createPlan("步骤");
            when(planGenerator.generate(anyString())).thenReturn(plan);
            when(planGenerator.replan(anyString(), anyList(), anyString())).thenReturn(plan);
            when(chatService.chatWithTools(anyString(), anyString(), any()))
                    .thenThrow(new LlmCallException("持续失败"));

            AgentExecutionResult result = executor.execute("测试", "conv-8");

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("重试次数超过上限");
            assertThat(result.mode()).isEqualTo(AgentMode.PLAN_AND_SOLVE);
            // 初始 generate 1 次 + replan 2 次（上限 2）
            verify(planGenerator, times(1)).generate(anyString());
            verify(planGenerator, times(2)).replan(anyString(), anyList(), anyString());
        }

        @Test
        @DisplayName("重新规划抛异常时返回失败结果")
        void should_returnFailure_when_replanThrows() {
            ExecutionPlan plan = createPlan("步骤");
            when(planGenerator.generate(anyString())).thenReturn(plan);
            when(planGenerator.replan(anyString(), anyList(), anyString()))
                    .thenThrow(new LlmCallException("重新规划失败"));
            when(chatService.chatWithTools(anyString(), anyString(), any()))
                    .thenThrow(new LlmCallException("步骤失败"));

            AgentExecutionResult result = executor.execute("测试", "conv-9");

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("重新规划失败");
        }

        @Test
        @DisplayName("重新生成的计划为空时返回失败结果")
        void should_returnFailure_when_replanReturnsEmpty() {
            ExecutionPlan plan = createPlan("步骤");
            ExecutionPlan emptyPlan = ExecutionPlan.of("目标", List.of());
            when(planGenerator.generate(anyString())).thenReturn(plan);
            when(planGenerator.replan(anyString(), anyList(), anyString())).thenReturn(emptyPlan);
            when(chatService.chatWithTools(anyString(), anyString(), any()))
                    .thenThrow(new LlmCallException("步骤失败"));

            AgentExecutionResult result = executor.execute("测试", "conv-10");

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("重新生成的计划为空");
        }

        @Test
        @DisplayName("失败步骤与续接步骤的记录全部保留")
        void should_keepAllStepRecords_when_replanOccurs() {
            ExecutionPlan failPlan = createPlan("失败步骤");
            ExecutionPlan remainingPlan = createPlan("补救步骤A", "补救步骤B");
            when(planGenerator.generate(anyString())).thenReturn(failPlan);
            when(planGenerator.replan(anyString(), anyList(), anyString())).thenReturn(remainingPlan);
            when(chatService.chatWithTools(anyString(), anyString(), any()))
                    .thenThrow(new LlmCallException("步骤执行失败"))
                    .thenReturn("补救结果A", "补救结果B");
            when(chatService.chatWithSystemPrompt(anyString(), anyString())).thenReturn("总结");

            AgentExecutionResult result = executor.execute("测试", "conv-14");

            assertThat(result.success()).isTrue();
            // 1 条失败记录 + 2 条成功记录
            assertThat(result.steps()).hasSize(3);
            assertThat(result.steps().get(0)).contains("失败");
            assertThat(result.iterations()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("计划生成与总结异常")
    class PlanAndSummaryFailures {

        @Test
        @DisplayName("计划生成异常时返回失败结果")
        void should_returnFailure_when_planGenerationFails() {
            when(planGenerator.generate(anyString())).thenThrow(new LlmCallException("计划生成失败"));

            AgentExecutionResult result = executor.execute("测试", "conv-5");

            assertThat(result.success()).isFalse();
            assertThat(result.mode()).isEqualTo(AgentMode.PLAN_AND_SOLVE);
            assertThat(result.errorMessage()).contains("计划生成失败");
            assertThat(result.iterations()).isZero();
        }

        @Test
        @DisplayName("生成的计划步骤为空时返回失败结果")
        void should_returnFailure_when_planStepsEmpty() {
            ExecutionPlan emptyPlan = ExecutionPlan.of("目标", List.of());
            when(planGenerator.generate(anyString())).thenReturn(emptyPlan);

            AgentExecutionResult result = executor.execute("测试", "conv-6");

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("计划为空");
        }

        @Test
        @DisplayName("总结生成失败时返回失败结果，步骤记录保留")
        void should_returnFailure_when_summaryGenerationFails() {
            ExecutionPlan plan = createPlan("步骤");
            when(planGenerator.generate(anyString())).thenReturn(plan);
            when(chatService.chatWithTools(anyString(), anyString(), any())).thenReturn("步骤结果");
            when(chatService.chatWithSystemPrompt(anyString(), anyString()))
                    .thenThrow(new LlmCallException("总结失败"));

            AgentExecutionResult result = executor.execute("测试", "conv-11");

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("总结生成失败");
            assertThat(result.steps()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("工作记忆（ADR-014）")
    class WorkingMemoryLifecycle {

        @Test
        @DisplayName("执行成功时写入步骤记录并在任务结束后清理")
        void should_putAndClearWorkingMemory_when_executionSucceeds() {
            WorkingMemory workingMemory = mock(WorkingMemory.class);
            when(workingMemoryProvider.getIfAvailable()).thenReturn(workingMemory);
            ExecutionPlan plan = createPlan("查询指标");
            when(planGenerator.generate(anyString())).thenReturn(plan);
            when(chatService.chatWithTools(anyString(), anyString(), any())).thenReturn("查询结果");
            when(chatService.chatWithSystemPrompt(anyString(), anyString())).thenReturn("总结");

            executor.execute("测试", "conv-wm1");

            verify(workingMemory).put(eq("conv-wm1"), eq(PlanAndSolveExecutor.WORKING_MEMORY_KEY),
                    org.mockito.ArgumentMatchers.contains("查询指标"));
            verify(workingMemory).clear("conv-wm1");
        }

        @Test
        @DisplayName("计划生成失败时不写入但仍在任务结束后清理")
        void should_clearWithoutPut_when_planGenerationFails() {
            WorkingMemory workingMemory = mock(WorkingMemory.class);
            when(workingMemoryProvider.getIfAvailable()).thenReturn(workingMemory);
            when(planGenerator.generate(anyString())).thenThrow(new LlmCallException("计划生成失败"));

            executor.execute("测试", "conv-wm2");

            verify(workingMemory, never()).put(anyString(), anyString(), anyString());
            verify(workingMemory).clear("conv-wm2");
        }

        @Test
        @DisplayName("conversationId 为 null 时不读写工作记忆")
        void should_skipWorkingMemory_when_conversationIdNull() {
            WorkingMemory workingMemory = mock(WorkingMemory.class);
            when(workingMemoryProvider.getIfAvailable()).thenReturn(workingMemory);
            ExecutionPlan plan = createPlan("步骤");
            when(planGenerator.generate(anyString())).thenReturn(plan);
            when(chatService.chatWithTools(isNull(), anyString(), any())).thenReturn("结果");
            when(chatService.chatWithSystemPrompt(anyString(), anyString())).thenReturn("总结");

            executor.execute("测试", null);

            verifyNoInteractions(workingMemory);
        }
    }

    @Nested
    @DisplayName("输入校验")
    class InputValidation {

        @Test
        @DisplayName("输入为 null 时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_inputNull() {
            assertThatThrownBy(() -> executor.execute(null, "conv-13"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("用户输入");
        }

        @Test
        @DisplayName("输入为空白时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_inputBlank() {
            assertThatThrownBy(() -> executor.execute("   ", "conv-14"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("用户输入");
        }

        @Test
        @DisplayName("输入为空字符串时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_inputEmpty() {
            assertThatThrownBy(() -> executor.execute("", "conv-15"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("conversationId 为 null 时不影响执行")
        void should_executeSuccessfully_when_conversationIdNull() {
            ExecutionPlan plan = createPlan("步骤");
            when(planGenerator.generate(anyString())).thenReturn(plan);
            when(chatService.chatWithTools(isNull(), anyString(), any())).thenReturn("结果");
            when(chatService.chatWithSystemPrompt(anyString(), anyString())).thenReturn("总结");

            AgentExecutionResult result = executor.execute("测试", null);

            assertThat(result.success()).isTrue();
        }
    }
}
