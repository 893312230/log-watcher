package com.smartops.agent.plan;

import com.smartops.common.exception.LlmCallException;
import com.smartops.common.model.ExecutionPlan;
import com.smartops.infrastructure.chat.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link PlanGenerator} 单元测试。
 *
 * <p>验证计划生成器的 LLM 调用、计划文本解析、降级策略、异常处理。
 * 对应 agent.md 阶段二任务8 的 Planner 组件。</p>
 *
 * <p><b>测试要点</b>：
 * <ul>
 *   <li>ChatService 必须 Mock，避免真实 LLM 调用导致测试不稳定</li>
 *   <li>parsePlan 为包级可见，可直接测试解析逻辑的各种输入格式</li>
 *   <li>降级策略：LLM 失败/空响应/无格式文本均应返回单步默认计划</li>
 * </ul></p>
 *
 * @author smartops
 * @since 1.0.0
 */
class PlanGeneratorTest {

    private ChatService chatService;
    private PlanGenerator planGenerator;

    @BeforeEach
    void setUp() {
        chatService = mock(ChatService.class);
        planGenerator = new PlanGenerator(chatService);
    }

    @Nested
    @DisplayName("计划生成（generate）")
    class PlanGeneration {

        @Test
        @DisplayName("正常解析 LLM 返回的多步骤计划文本")
        void should_parsePlan_when_llmReturnsValidPlan() {
            String llmResponse = """
                    1. 查询CPU使用率 -> 查询CPU当前使用率指标
                    2. 分析趋势 -> 分析CPU最近一小时趋势
                    3. 生成报告 -> 综合结果生成CPU分析报告
                    """;
            when(chatService.chatWithSystemPrompt(anyString(), anyString())).thenReturn(llmResponse);

            ExecutionPlan plan = planGenerator.generate("分析CPU情况");

            assertThat(plan.steps()).hasSize(3);
            assertThat(plan.steps().get(0).description()).isEqualTo("查询CPU使用率");
            assertThat(plan.steps().get(0).action()).isEqualTo("查询CPU当前使用率指标");
            assertThat(plan.steps().get(1).index()).isEqualTo(1);
            assertThat(plan.steps().get(2).description()).isEqualTo("生成报告");
            // generate 用 userInput 作为计划目标
            assertThat(plan.goal()).isEqualTo("分析CPU情况");
        }

        @Test
        @DisplayName("LLM 返回空字符串时返回默认计划")
        void should_returnDefaultPlan_when_llmReturnsEmpty() {
            when(chatService.chatWithSystemPrompt(anyString(), anyString())).thenReturn("");

            ExecutionPlan plan = planGenerator.generate("查询CPU");

            assertThat(plan.steps()).hasSize(1);
            assertThat(plan.steps().get(0).description()).isEqualTo("直接处理用户请求（计划生成降级）");
            // 默认计划的动作应为 userInput，使执行器能转发原始请求
            assertThat(plan.steps().get(0).action()).isEqualTo("查询CPU");
            assertThat(plan.goal()).isEqualTo("查询CPU");
        }

        @Test
        @DisplayName("LLM 返回 null 时返回默认计划")
        void should_returnDefaultPlan_when_llmReturnsNull() {
            when(chatService.chatWithSystemPrompt(anyString(), anyString())).thenReturn(null);

            ExecutionPlan plan = planGenerator.generate("查询CPU");

            assertThat(plan.steps()).hasSize(1);
            assertThat(plan.steps().get(0).description()).isEqualTo("直接处理用户请求（计划生成降级）");
            assertThat(plan.steps().get(0).action()).isEqualTo("查询CPU");
        }

        @Test
        @DisplayName("LLM 返回无格式文本时返回默认计划")
        void should_returnDefaultPlan_when_llmReturnsUnformattedText() {
            when(chatService.chatWithSystemPrompt(anyString(), anyString())).thenReturn("这是一段没有格式的文本，不包含步骤");

            ExecutionPlan plan = planGenerator.generate("查询CPU");

            assertThat(plan.steps()).hasSize(1);
            assertThat(plan.steps().get(0).description()).isEqualTo("直接处理用户请求（计划生成降级）");
            assertThat(plan.steps().get(0).action()).isEqualTo("查询CPU");
        }

        @Test
        @DisplayName("LLM 调用抛异常时返回默认计划")
        void should_returnDefaultPlan_when_llmThrowsException() {
            when(chatService.chatWithSystemPrompt(anyString(), anyString()))
                    .thenThrow(new LlmCallException("LLM 服务不可用"));

            ExecutionPlan plan = planGenerator.generate("查询CPU");

            assertThat(plan.steps()).hasSize(1);
            assertThat(plan.steps().get(0).description()).isEqualTo("直接处理用户请求（计划生成降级）");
            assertThat(plan.steps().get(0).action()).isEqualTo("查询CPU");
        }

        @Test
        @DisplayName("计划步骤数与 LLM 返回的有效步骤数一致")
        void should_returnCorrectStepCount_when_multipleSteps() {
            String llmResponse = """
                    1. 步骤一 -> 动作一
                    2. 步骤二 -> 动作二
                    3. 步骤三 -> 动作三
                    4. 步骤四 -> 动作四
                    5. 步骤五 -> 动作五
                    """;
            when(chatService.chatWithSystemPrompt(anyString(), anyString())).thenReturn(llmResponse);

            ExecutionPlan plan = planGenerator.generate("复杂任务");

            assertThat(plan.stepCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("LLM 返回单步有效计划时正确解析（非默认计划）")
        void should_parseSingleStepPlan_when_llmReturnsOneValidStep() {
            // 单步有效计划：描述不是"直接处理用户请求"，应被识别为非默认计划
            String llmResponse = "1. 查询CPU指标 -> 查询CPU当前使用率";
            when(chatService.chatWithSystemPrompt(anyString(), anyString())).thenReturn(llmResponse);

            ExecutionPlan plan = planGenerator.generate("查询CPU");

            assertThat(plan.steps()).hasSize(1);
            assertThat(plan.steps().get(0).description()).isEqualTo("查询CPU指标");
            assertThat(plan.steps().get(0).action()).isEqualTo("查询CPU当前使用率");
            assertThat(plan.goal()).isEqualTo("查询CPU");
        }

        @Test
        @DisplayName("LLM 返回混合格式时只解析有效步骤")
        void should_parseOnlyValidSteps_when_mixedFormat() {
            String llmResponse = """
                    这是一段说明文字
                    1. 查询指标 -> 查询CPU指标
                    无效行
                    2. 分析结果 -> 分析结果
                    """;
            when(chatService.chatWithSystemPrompt(anyString(), anyString())).thenReturn(llmResponse);

            ExecutionPlan plan = planGenerator.generate("测试");

            assertThat(plan.steps()).hasSize(2);
            assertThat(plan.steps().get(0).description()).isEqualTo("查询指标");
            assertThat(plan.steps().get(1).description()).isEqualTo("分析结果");
        }
    }

    @Nested
    @DisplayName("parsePlan 方法解析各种格式")
    class ParsePlanFormats {

        @Test
        @DisplayName("解析标准格式（序号. 描述 -> 动作）")
        void should_parseStandardFormat() {
            String response = "1. 查询指标 -> 查询CPU指标\n2. 分析结果 -> 分析查询结果";

            ExecutionPlan plan = planGenerator.parsePlan(response);

            assertThat(plan.steps()).hasSize(2);
            assertThat(plan.steps().get(0).description()).isEqualTo("查询指标");
            assertThat(plan.steps().get(0).action()).isEqualTo("查询CPU指标");
            assertThat(plan.steps().get(1).description()).isEqualTo("分析结果");
            assertThat(plan.steps().get(1).action()).isEqualTo("分析查询结果");
        }

        @Test
        @DisplayName("解析中文序号分隔符（序号、描述 -> 动作）")
        void should_parseChineseSeparator() {
            String response = "1、查询指标 -> 查询CPU指标";

            ExecutionPlan plan = planGenerator.parsePlan(response);

            assertThat(plan.steps()).hasSize(1);
            assertThat(plan.steps().get(0).description()).isEqualTo("查询指标");
        }

        @Test
        @DisplayName("解析括号序号分隔符（序号) 描述 -> 动作）")
        void should_parseParenthesisSeparator() {
            String response = "1) 查询指标 -> 查询CPU指标";

            ExecutionPlan plan = planGenerator.parsePlan(response);

            assertThat(plan.steps()).hasSize(1);
            assertThat(plan.steps().get(0).description()).isEqualTo("查询指标");
        }

        @Test
        @DisplayName("解析 Unicode 箭头（序号. 描述 → 动作）")
        void should_parseUnicodeArrow() {
            String response = "1. 查询指标 → 查询CPU指标";

            ExecutionPlan plan = planGenerator.parsePlan(response);

            assertThat(plan.steps()).hasSize(1);
            assertThat(plan.steps().get(0).action()).isEqualTo("查询CPU指标");
        }

        @Test
        @DisplayName("跳过不匹配的行，只解析有效步骤")
        void should_skipInvalidLines() {
            String response = """
                    这是一段说明文字
                    1. 查询指标 -> 查询CPU指标
                    无效行
                    2. 分析结果 -> 分析结果
                    """;

            ExecutionPlan plan = planGenerator.parsePlan(response);

            assertThat(plan.steps()).hasSize(2);
            assertThat(plan.steps().get(0).description()).isEqualTo("查询指标");
            assertThat(plan.steps().get(1).description()).isEqualTo("分析结果");
        }

        @Test
        @DisplayName("解析带额外空白的行")
        void should_parseWithExtraWhitespace() {
            String response = "  1.   查询指标   ->   查询CPU指标  ";

            ExecutionPlan plan = planGenerator.parsePlan(response);

            assertThat(plan.steps()).hasSize(1);
            assertThat(plan.steps().get(0).description()).isEqualTo("查询指标");
            assertThat(plan.steps().get(0).action()).isEqualTo("查询CPU指标");
        }

        @Test
        @DisplayName("空字符串返回默认计划")
        void should_returnDefaultPlan_when_emptyResponse() {
            ExecutionPlan plan = planGenerator.parsePlan("");

            assertThat(plan.steps()).hasSize(1);
            assertThat(plan.steps().get(0).description()).isEqualTo("直接处理用户请求（计划生成降级）");
        }

        @Test
        @DisplayName("null 返回默认计划")
        void should_returnDefaultPlan_when_nullResponse() {
            ExecutionPlan plan = planGenerator.parsePlan(null);

            assertThat(plan.steps()).hasSize(1);
            assertThat(plan.steps().get(0).description()).isEqualTo("直接处理用户请求（计划生成降级）");
        }

        @Test
        @DisplayName("纯空白字符串返回默认计划")
        void should_returnDefaultPlan_when_blankResponse() {
            ExecutionPlan plan = planGenerator.parsePlan("   \n  \t  ");

            assertThat(plan.steps()).hasSize(1);
            assertThat(plan.steps().get(0).description()).isEqualTo("直接处理用户请求（计划生成降级）");
        }

        @Test
        @DisplayName("无匹配行的文本返回默认计划")
        void should_returnDefaultPlan_when_noMatchingLines() {
            ExecutionPlan plan = planGenerator.parsePlan("没有任何格式的文本");

            assertThat(plan.steps()).hasSize(1);
            assertThat(plan.steps().get(0).description()).isEqualTo("直接处理用户请求（计划生成降级）");
        }

        @Test
        @DisplayName("步骤序号从 0 开始连续递增")
        void should_assignSequentialIndices() {
            String response = """
                    1. 步骤一 -> 动作一
                    2. 步骤二 -> 动作二
                    3. 步骤三 -> 动作三
                    """;

            ExecutionPlan plan = planGenerator.parsePlan(response);

            assertThat(plan.steps().get(0).index()).isEqualTo(0);
            assertThat(plan.steps().get(1).index()).isEqualTo(1);
            assertThat(plan.steps().get(2).index()).isEqualTo(2);
        }

        @Test
        @DisplayName("LLM 返回不连续序号时仍按解析顺序赋 0 基序号")
        void should_reindex_when_llmReturnsNonSequentialNumbers() {
            String response = """
                    3. 步骤A -> 动作A
                    7. 步骤B -> 动作B
                    """;

            ExecutionPlan plan = planGenerator.parsePlan(response);

            assertThat(plan.steps()).hasSize(2);
            assertThat(plan.steps().get(0).index()).isEqualTo(0);
            assertThat(plan.steps().get(1).index()).isEqualTo(1);
            assertThat(plan.steps().get(0).description()).isEqualTo("步骤A");
        }
    }

    @Nested
    @DisplayName("重新规划（replan）")
    class Replanning {

        @Test
        @DisplayName("正常解析剩余工作计划，goal 为原始请求")
        void should_parseRemainingPlan_when_replanResponseValid() {
            String llmResponse = """
                    1. 更换方式查询指标 -> 改用区间查询获取CPU指标
                    2. 生成报告 -> 综合结果生成报告
                    """;
            when(chatService.chatWithSystemPrompt(anyString(), anyString())).thenReturn(llmResponse);

            ExecutionPlan plan = planGenerator.replan("分析CPU情况",
                    List.of("步骤1 [查询指标]: 失败原因X"), "步骤执行失败");

            assertThat(plan.steps()).hasSize(2);
            assertThat(plan.steps().get(0).description()).isEqualTo("更换方式查询指标");
            assertThat(plan.goal()).isEqualTo("分析CPU情况");
        }

        @Test
        @DisplayName("replan 上下文包含已完成记录与失败原因")
        void should_passContextToLlm_when_replan() {
            when(chatService.chatWithSystemPrompt(anyString(), anyString()))
                    .thenReturn("1. 补救步骤 -> 补救动作");

            planGenerator.replan("原始请求", List.of("步骤1 [查询]: 结果A"), "连接超时");

            org.mockito.ArgumentCaptor<String> contextCaptor =
                    org.mockito.ArgumentCaptor.forClass(String.class);
            org.mockito.Mockito.verify(chatService)
                    .chatWithSystemPrompt(anyString(), contextCaptor.capture());
            assertThat(contextCaptor.getValue())
                    .contains("原始请求")
                    .contains("步骤1 [查询]: 结果A")
                    .contains("连接超时");
        }

        @Test
        @DisplayName("已完成记录为 null 时上下文只含原始请求与失败原因")
        void should_replan_when_completedRecordsNull() {
            when(chatService.chatWithSystemPrompt(anyString(), anyString()))
                    .thenReturn("1. 补救步骤 -> 补救动作");

            ExecutionPlan plan = planGenerator.replan("原始请求", null, null);

            assertThat(plan.steps()).hasSize(1);
            assertThat(plan.steps().get(0).description()).isEqualTo("补救步骤");
        }

        @Test
        @DisplayName("replan 响应无法解析时返回带降级标记的默认计划")
        void should_returnDefaultPlan_when_replanResponseUnparseable() {
            when(chatService.chatWithSystemPrompt(anyString(), anyString())).thenReturn("无格式文本");

            ExecutionPlan plan = planGenerator.replan("查询CPU", List.of(), "步骤失败");

            assertThat(plan.steps()).hasSize(1);
            assertThat(plan.steps().get(0).description()).isEqualTo("直接处理用户请求（计划生成降级）");
            assertThat(plan.steps().get(0).action()).isEqualTo("查询CPU");
        }

        @Test
        @DisplayName("replan LLM 调用异常时返回带降级标记的默认计划")
        void should_returnDefaultPlan_when_replanLlmThrows() {
            when(chatService.chatWithSystemPrompt(anyString(), anyString()))
                    .thenThrow(new LlmCallException("LLM 不可用"));

            ExecutionPlan plan = planGenerator.replan("查询CPU", List.of(), "步骤失败");

            assertThat(plan.steps()).hasSize(1);
            assertThat(plan.steps().get(0).description()).isEqualTo("直接处理用户请求（计划生成降级）");
        }

        @Test
        @DisplayName("replan 输入为 null 或空白时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_replanInputInvalid() {
            assertThatThrownBy(() -> planGenerator.replan(null, List.of(), "原因"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> planGenerator.replan("  ", List.of(), "原因"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("异常处理")
    class ExceptionHandling {

        @Test
        @DisplayName("输入为 null 时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_inputNull() {
            assertThatThrownBy(() -> planGenerator.generate(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("用户输入");
        }

        @Test
        @DisplayName("输入为空白时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_inputBlank() {
            assertThatThrownBy(() -> planGenerator.generate("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("用户输入");
        }

        @Test
        @DisplayName("输入为空字符串时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_inputEmpty() {
            assertThatThrownBy(() -> planGenerator.generate(""))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
