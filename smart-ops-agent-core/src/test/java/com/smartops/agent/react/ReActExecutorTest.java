package com.smartops.agent.react;

import com.smartops.agent.tools.PrometheusTools;
import com.smartops.common.enums.AgentMode;
import com.smartops.common.exception.LlmCallException;
import com.smartops.common.model.AgentExecutionResult;
import com.smartops.infrastructure.advisor.ToolCallRoundGate;
import com.smartops.infrastructure.chat.ChatService;
import com.smartops.infrastructure.memory.WorkingMemory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ReActExecutor} 单元测试。
 *
 * <p>验证 ReAct 执行器的核心契约：正常执行返回成功结果、ChatService 异常时返回失败结果、
 * 输入校验（null/空白）、自定义最大迭代次数、返回结果的模式与步骤记录。
 * 对应 agent.md 阶段二特性6。</p>
 *
 * <p><b>测试策略</b>：
 * <ul>
 *   <li>使用 Mockito mock {@link ChatService} 与 {@link PrometheusTools}，
 *       避免真实 LLM 调用</li>
 *   <li>使用 AssertJ 断言，遵循 Arrange-Act-Assert 三段式</li>
 *   <li>使用 {@code @Nested} 分组，{@code @DisplayName} 中文描述</li>
 *   <li>测试方法命名 {@code should_{期望行为}_when_{前置条件}}</li>
 * </ul></p>
 *
 * @author smartops
 * @since 1.0.0
 */
class ReActExecutorTest {

    private ChatService chatService;
    private PrometheusTools prometheusTools;
    private ObjectProvider<WorkingMemory> workingMemoryProvider;
    private ReActExecutor executor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        chatService = mock(ChatService.class);
        prometheusTools = mock(PrometheusTools.class);
        // 默认 getIfAvailable() 返回 null：全部既有用例同时覆盖"工作记忆未启用"降级分支
        workingMemoryProvider = mock(ObjectProvider.class);
        executor = new ReActExecutor(chatService, prometheusTools, workingMemoryProvider);
    }

    @AfterEach
    void tearDown() {
        // 防御性清理，防止线程级门状态跨用例泄露
        ToolCallRoundGate.clearRequest();
    }

    @Nested
    @DisplayName("正常执行")
    class NormalExecution {

        @Test
        @DisplayName("正常执行返回成功结果，包含 LLM 答案与执行步骤")
        void should_returnSuccessResult_when_validInput() {
            // Arrange
            String userInput = "查询 CPU 使用率";
            String conversationId = "conv-001";
            String llmAnswer = "当前 CPU 使用率为 45.2%";
            when(chatService.chatWithTools(anyString(), anyString(), any())).thenReturn(llmAnswer);

            // Act
            AgentExecutionResult result = executor.execute(userInput, conversationId);

            // Assert
            assertThat(result.success()).isTrue();
            assertThat(result.answer()).isEqualTo(llmAnswer);
            assertThat(result.errorMessage()).isNull();
            assertThat(result.iterations()).isEqualTo(1);
            verify(chatService).chatWithTools(eq(conversationId), eq(userInput), eq(prometheusTools));
        }

        @Test
        @DisplayName("返回结果包含正确的 AgentMode.REACT 模式")
        void should_returnReactMode_when_executionSucceeds() {
            // Arrange
            when(chatService.chatWithTools(anyString(), anyString(), any())).thenReturn("答案");

            // Act
            AgentExecutionResult result = executor.execute("查询内存", "conv-002");

            // Assert
            assertThat(result.mode()).isEqualTo(AgentMode.REACT);
        }

        @Test
        @DisplayName("返回结果的 steps 不为空，包含执行开始与完成记录")
        void should_returnNonEmptySteps_when_executionSucceeds() {
            // Arrange
            when(chatService.chatWithTools(anyString(), anyString(), any())).thenReturn("答案");

            // Act
            AgentExecutionResult result = executor.execute("查询磁盘", "conv-003");

            // Assert
            assertThat(result.steps()).isNotEmpty();
            assertThat(result.steps()).hasSize(2);
            // 第一步为执行开始
            assertThat(result.steps().get(0)).contains("ReAct 执行开始");
            assertThat(result.steps().get(0)).contains("conv-003");
            // 第二步为执行完成
            assertThat(result.steps().get(1)).contains("ReAct 执行完成");
            assertThat(result.steps().get(1)).contains("查询磁盘");
        }

        @Test
        @DisplayName("使用默认最大迭代次数 10 时，步骤中记录 MAX_ITERATIONS")
        void should_recordDefaultMaxIterations_when_noMaxIterationsSpecified() {
            // Arrange
            when(chatService.chatWithTools(anyString(), anyString(), any())).thenReturn("答案");

            // Act
            AgentExecutionResult result = executor.execute("查询 QPS", "conv-004");

            // Assert
            assertThat(result.steps().get(0)).contains("最大迭代次数: " + ReActExecutor.MAX_ITERATIONS);
        }
    }

    @Nested
    @DisplayName("自定义最大迭代次数")
    class CustomMaxIterations {

        @Test
        @DisplayName("自定义 maxIterations 参数生效，步骤中记录指定的迭代次数")
        void should_useCustomMaxIterations_when_maxIterationsProvided() {
            // Arrange
            String userInput = "分析告警原因";
            String conversationId = "conv-005";
            int customMaxIterations = 5;
            when(chatService.chatWithTools(anyString(), anyString(), any())).thenReturn("告警原因为 CPU 过载");

            // Act
            AgentExecutionResult result = executor.execute(userInput, conversationId, customMaxIterations);

            // Assert
            assertThat(result.success()).isTrue();
            // 自定义 maxIterations 应被记录在执行开始步骤中
            assertThat(result.steps().get(0)).contains("最大迭代次数: 5");
            assertThat(result.steps().get(0)).doesNotContain("最大迭代次数: 10");
            // chatWithTools 仍被正常调用
            verify(chatService).chatWithTools(eq(conversationId), eq(userInput), eq(prometheusTools));
        }

        @Test
        @DisplayName("maxIterations 为 1 时正常执行")
        void should_executeSuccessfully_when_maxIterationsIsOne() {
            // Arrange
            when(chatService.chatWithTools(anyString(), anyString(), any())).thenReturn("答案");

            // Act
            AgentExecutionResult result = executor.execute("查询 CPU", "conv-006", 1);

            // Assert
            assertThat(result.success()).isTrue();
            assertThat(result.steps().get(0)).contains("最大迭代次数: 1");
        }
    }

    @Nested
    @DisplayName("异常处理")
    class ExceptionHandling {

        @Test
        @DisplayName("ChatService 抛出平台异常时返回失败结果，包含错误信息")
        void should_returnFailureResult_when_chatServiceThrowsException() {
            // Arrange
            String userInput = "查询 CPU";
            String conversationId = "conv-007";
            String errorMessage = "LLM 服务不可用";
            when(chatService.chatWithTools(anyString(), anyString(), any()))
                    .thenThrow(new LlmCallException(errorMessage));

            // Act
            AgentExecutionResult result = executor.execute(userInput, conversationId);

            // Assert
            assertThat(result.success()).isFalse();
            assertThat(result.answer()).isNull();
            assertThat(result.errorMessage()).isEqualTo(errorMessage);
            assertThat(result.mode()).isEqualTo(AgentMode.REACT);
            assertThat(result.iterations()).isEqualTo(0);
            // 失败时仍应记录执行开始步骤与失败步骤
            assertThat(result.steps()).isNotEmpty();
            assertThat(result.steps().get(0)).contains("ReAct 执行开始");
            assertThat(result.steps().get(1)).contains("ReAct 执行失败");
            assertThat(result.steps().get(1)).contains(errorMessage);
        }

        @Test
        @DisplayName("ChatService 抛出非平台异常（编程错误）时向上传播，不吞为失败结果")
        void should_propagate_when_chatServiceThrowsNonPlatformException() {
            // Arrange
            when(chatService.chatWithTools(anyString(), anyString(), any()))
                    .thenThrow(new IllegalArgumentException("参数错误"));

            // Act & Assert
            assertThatThrownBy(() -> executor.execute("查询", "conv-008"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("参数错误");
        }

        @Test
        @DisplayName("输入为 null 时抛出 IllegalArgumentException")
        void should_throwIllegalArgument_when_inputIsNull() {
            assertThatThrownBy(() -> executor.execute(null, "conv-009"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不能为 null 或空白");
        }

        @Test
        @DisplayName("输入为空白字符串时抛出 IllegalArgumentException")
        void should_throwIllegalArgument_when_inputIsBlank() {
            assertThatThrownBy(() -> executor.execute("   ", "conv-010"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不能为 null 或空白");
        }

        @Test
        @DisplayName("输入为空字符串时抛出 IllegalArgumentException")
        void should_throwIllegalArgument_when_inputIsEmpty() {
            assertThatThrownBy(() -> executor.execute("", "conv-011"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不能为 null 或空白");
        }

        @Test
        @DisplayName("maxIterations 为 0 时抛出 IllegalArgumentException")
        void should_throwIllegalArgument_when_maxIterationsIsZero() {
            assertThatThrownBy(() -> executor.execute("查询 CPU", "conv-012", 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("最大迭代次数必须为正数");
        }

        @Test
        @DisplayName("maxIterations 为负数时抛出 IllegalArgumentException")
        void should_throwIllegalArgument_when_maxIterationsIsNegative() {
            assertThatThrownBy(() -> executor.execute("查询 CPU", "conv-013", -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("最大迭代次数必须为正数");
        }
    }

    @Nested
    @DisplayName("工具调用轮次门（ToolCallRoundGate）")
    class ToolCallGateLifecycle {

        @Test
        @DisplayName("成功执行后门状态被清理，无跨请求泄露")
        void should_clearGate_when_executionSucceeds() {
            when(chatService.chatWithTools(anyString(), anyString(), any())).thenReturn("答案");

            executor.execute("查询 CPU", "conv-g1");

            // ChatService 被 Mock，未经过真实工具循环，完成轮次为 0；finally 已清理门状态
            assertThat(ToolCallRoundGate.lastCompletedRounds()).isZero();
        }

        @Test
        @DisplayName("执行异常后门状态同样被清理")
        void should_clearGate_when_executionFails() {
            when(chatService.chatWithTools(anyString(), anyString(), any()))
                    .thenThrow(new LlmCallException("LLM 不可用"));

            executor.execute("查询 CPU", "conv-g2");

            assertThat(ToolCallRoundGate.lastCompletedRounds()).isZero();
        }

        @Test
        @DisplayName("无工具循环时迭代次数为 1（仅初始 LLM 调用）")
        void should_reportOneIteration_when_noToolRounds() {
            when(chatService.chatWithTools(anyString(), anyString(), any())).thenReturn("答案");

            AgentExecutionResult result = executor.execute("查询 CPU", "conv-g3");

            assertThat(result.iterations()).isEqualTo(1);
            assertThat(result.steps().get(1)).contains("实际迭代轮次: 1");
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
            when(chatService.chatWithTools(anyString(), anyString(), any())).thenReturn("答案");

            executor.execute("查询 CPU", "conv-w1");

            verify(workingMemory).put(eq("conv-w1"), eq(ReActExecutor.WORKING_MEMORY_KEY),
                    org.mockito.ArgumentMatchers.contains("ReAct 执行完成"));
            verify(workingMemory).clear("conv-w1");
        }

        @Test
        @DisplayName("执行失败时仍写入失败步骤并在任务结束后清理")
        void should_putAndClearWorkingMemory_when_executionFails() {
            WorkingMemory workingMemory = mock(WorkingMemory.class);
            when(workingMemoryProvider.getIfAvailable()).thenReturn(workingMemory);
            when(chatService.chatWithTools(anyString(), anyString(), any()))
                    .thenThrow(new LlmCallException("LLM 不可用"));

            executor.execute("查询 CPU", "conv-w2");

            verify(workingMemory).put(eq("conv-w2"), eq(ReActExecutor.WORKING_MEMORY_KEY),
                    org.mockito.ArgumentMatchers.contains("ReAct 执行失败"));
            verify(workingMemory).clear("conv-w2");
        }

        @Test
        @DisplayName("conversationId 为 null 时不读写工作记忆")
        void should_skipWorkingMemory_when_conversationIdNull() {
            WorkingMemory workingMemory = mock(WorkingMemory.class);
            when(workingMemoryProvider.getIfAvailable()).thenReturn(workingMemory);
            when(chatService.chatWithTools(isNull(), anyString(), any())).thenReturn("答案");

            executor.execute("查询 CPU", null);

            verifyNoInteractions(workingMemory);
        }
    }

    @Nested
    @DisplayName("默认迭代次数常量")
    class MaxIterationsConstant {

        @Test
        @DisplayName("MAX_ITERATIONS 常量值为 10")
        void should_beTen_when_checkMaxIterationsConstant() {
            assertThat(ReActExecutor.MAX_ITERATIONS).isEqualTo(10);
        }
    }
}
