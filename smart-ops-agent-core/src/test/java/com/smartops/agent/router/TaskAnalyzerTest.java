package com.smartops.agent.router;

import com.smartops.common.enums.AgentMode;
import com.smartops.common.enums.IntentType;
import com.smartops.common.model.IntentResult;
import com.smartops.common.model.TaskComplexity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link TaskAnalyzer} 单元测试。
 *
 * <p>验证任务复杂度分析器的步骤数预估、依赖关系检测、实时性/探索性判断、
 * 以及 ReAct / Plan-and-Solve 模式选择逻辑。
 * 对应 agent.md 阶段二任务6。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class TaskAnalyzerTest {

    private TaskAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new TaskAnalyzer();
    }

    @Nested
    @DisplayName("简单任务")
    class SimpleTasks {

        @Test
        @DisplayName("查询指标为 1 步 REACT 任务")
        void should_returnSimpleReAct_when_queryMetric() {
            IntentResult intent = new IntentResult(IntentType.QUERY_METRIC, 0.85, IntentResult.SOURCE_L1_REGEX, null);

            TaskComplexity complexity = analyzer.analyze("查询 CPU 使用率", intent);

            assertThat(complexity.estimatedSteps()).isEqualTo(1);
            assertThat(complexity.hasDependencies()).isFalse();
            assertThat(complexity.suggestedMode()).isEqualTo(AgentMode.REACT);
            assertThat(complexity.isSimple()).isTrue();
            assertThat(complexity.steps()).hasSize(1);
            assertThat(complexity.steps().get(0)).contains("查询");
        }

        @Test
        @DisplayName("知识问答为 1 步 REACT 任务")
        void should_returnSimpleReAct_when_knowledgeQA() {
            IntentResult intent = new IntentResult(IntentType.KNOWLEDGE_QA, 0.8, IntentResult.SOURCE_L1_REGEX, null);

            TaskComplexity complexity = analyzer.analyze("如何配置 Nginx", intent);

            assertThat(complexity.estimatedSteps()).isEqualTo(1);
            assertThat(complexity.suggestedMode()).isEqualTo(AgentMode.REACT);
        }

        @Test
        @DisplayName("UNKNOWN 意图为 1 步 REACT 任务")
        void should_returnSimpleReAct_when_unknownIntent() {
            IntentResult intent = new IntentResult(IntentType.UNKNOWN, 0.1, IntentResult.SOURCE_L4_LLM, null);

            TaskComplexity complexity = analyzer.analyze("今天天气怎么样", intent);

            assertThat(complexity.estimatedSteps()).isEqualTo(1);
            assertThat(complexity.suggestedMode()).isEqualTo(AgentMode.REACT);
        }
    }

    @Nested
    @DisplayName("中等任务")
    class MediumTasks {

        @Test
        @DisplayName("趋势分析为 2 步 REACT 任务")
        void should_returnTwoStepReAct_when_trendAnalysis() {
            IntentResult intent = new IntentResult(IntentType.TREND_ANALYSIS, 0.85, IntentResult.SOURCE_L1_REGEX, null);

            TaskComplexity complexity = analyzer.analyze("最近 CPU 趋势", intent);

            assertThat(complexity.estimatedSteps()).isEqualTo(2);
            assertThat(complexity.suggestedMode()).isEqualTo(AgentMode.REACT);
            assertThat(complexity.steps()).hasSize(2);
        }

        @Test
        @DisplayName("告警分析为 2 步 REACT 任务且实时")
        void should_returnRealtimeReAct_when_alertAnalysis() {
            IntentResult intent = new IntentResult(IntentType.ANALYZE_ALERT, 0.85, IntentResult.SOURCE_L1_REGEX, null);

            TaskComplexity complexity = analyzer.analyze("分析当前告警", intent);

            assertThat(complexity.estimatedSteps()).isEqualTo(2);
            assertThat(complexity.suggestedMode()).isEqualTo(AgentMode.REACT);
            assertThat(complexity.realTimeRequired()).isTrue();
        }
    }

    @Nested
    @DisplayName("探索性任务")
    class ExploratoryTasks {

        @Test
        @DisplayName("根因分析为 3 步探索性 REACT 任务")
        void should_returnExploratoryReAct_when_rootCause() {
            IntentResult intent = new IntentResult(IntentType.ROOT_CAUSE, 0.85, IntentResult.SOURCE_L1_REGEX, null);

            TaskComplexity complexity = analyzer.analyze("为什么服务响应变慢", intent);

            assertThat(complexity.estimatedSteps()).isEqualTo(3);
            assertThat(complexity.isExploratory()).isTrue();
            assertThat(complexity.suggestedMode()).isEqualTo(AgentMode.REACT);
        }

        @Test
        @DisplayName("含排查关键词标记为探索性")
        void should_markExploratory_when_containsDiagnose() {
            IntentResult intent = new IntentResult(IntentType.UNKNOWN, 0.3, IntentResult.SOURCE_L2_KEYWORD, null);

            TaskComplexity complexity = analyzer.analyze("排查网络问题", intent);

            assertThat(complexity.isExploratory()).isTrue();
        }
    }

    @Nested
    @DisplayName("复杂任务")
    class ComplexTasks {

        @Test
        @DisplayName("单步操作为 REACT 任务")
        void should_returnReAct_when_singleOperation() {
            IntentResult intent = new IntentResult(IntentType.EXECUTE_OPERATION, 0.9, IntentResult.SOURCE_L1_REGEX, null);

            TaskComplexity complexity = analyzer.analyze("重启订单服务", intent);

            assertThat(complexity.estimatedSteps()).isEqualTo(1);
            assertThat(complexity.suggestedMode()).isEqualTo(AgentMode.REACT);
        }

        @Test
        @DisplayName("多步骤操作+连接词为 PLAN_AND_SOLVE 任务")
        void should_returnPlanAndSolve_when_multiStepWithConnectors() {
            IntentResult intent = new IntentResult(IntentType.EXECUTE_OPERATION, 0.9, IntentResult.SOURCE_L1_REGEX, null);

            TaskComplexity complexity = analyzer.analyze("重启订单服务然后清理缓存", intent);

            assertThat(complexity.estimatedSteps()).isGreaterThanOrEqualTo(2);
            assertThat(complexity.hasDependencies()).isTrue();
            assertThat(complexity.suggestedMode()).isEqualTo(AgentMode.PLAN_AND_SOLVE);
        }

        @Test
        @DisplayName("3 个以上步骤为 PLAN_AND_SOLVE 任务")
        void should_returnPlanAndSolve_when_threeOrMoreSteps() {
            IntentResult intent = new IntentResult(IntentType.EXECUTE_OPERATION, 0.9, IntentResult.SOURCE_L1_REGEX, null);

            // 2 个动作词与 2 个连接词推断取较大者：max(2, 2+1) = 3 步，非探索性 → PLAN_AND_SOLVE
            TaskComplexity complexity = analyzer.analyze(
                    "重启服务 然后清理缓存 接着验证结果", intent);

            assertThat(complexity.estimatedSteps()).isGreaterThanOrEqualTo(3);
            assertThat(complexity.suggestedMode()).isEqualTo(AgentMode.PLAN_AND_SOLVE);
        }

        @Test
        @DisplayName("多动作无连接词但有依赖时为 PLAN_AND_SOLVE")
        void should_returnPlanAndSolve_when_multipleActionsWithDeps() {
            IntentResult intent = new IntentResult(IntentType.EXECUTE_OPERATION, 0.9, IntentResult.SOURCE_L1_REGEX, null);

            // 2 个动作词 → countActions=2 → stepCount=2，detectDependencies 检测到 2+ 动作 → 有依赖
            TaskComplexity complexity = analyzer.analyze("重启扩容集群", intent);

            assertThat(complexity.estimatedSteps()).isEqualTo(2);
            assertThat(complexity.hasDependencies()).isTrue();
            assertThat(complexity.suggestedMode()).isEqualTo(AgentMode.PLAN_AND_SOLVE);
        }
    }

    @Nested
    @DisplayName("步骤摘要")
    class StepSummaries {

        @Test
        @DisplayName("查询指标步骤摘要正确生成")
        void should_generateCorrectSteps_when_queryMetric() {
            IntentResult intent = new IntentResult(IntentType.QUERY_METRIC, 0.85, IntentResult.SOURCE_L1_REGEX, null);

            TaskComplexity complexity = analyzer.analyze("查询 CPU", intent);

            assertThat(complexity.steps()).hasSize(1);
            assertThat(complexity.steps().get(0)).isEqualTo("查询指标数据");
        }

        @Test
        @DisplayName("根因分析步骤摘要包含定位根因")
        void should_generateCorrectSteps_when_rootCause() {
            IntentResult intent = new IntentResult(IntentType.ROOT_CAUSE, 0.85, IntentResult.SOURCE_L1_REGEX, null);

            TaskComplexity complexity = analyzer.analyze("为什么服务变慢", intent);

            assertThat(complexity.steps()).hasSize(3);
            assertThat(complexity.steps()).contains("收集系统状态", "分析异常指标", "定位根因");
        }

        @Test
        @DisplayName("步骤数不超过 10 的上限")
        void should_capAt10_when_excessiveConnectors() {
            IntentResult intent = new IntentResult(IntentType.QUERY_METRIC, 0.85, IntentResult.SOURCE_L1_REGEX, null);

            // 构造超过 10 个连接词的输入
            String input = "查询 CPU 然后 接着 之后 再 最后 首先 其次 然后 接着 之后 再 最后";
            TaskComplexity complexity = analyzer.analyze(input, intent);

            assertThat(complexity.estimatedSteps()).isLessThanOrEqualTo(10);
        }

        @Test
        @DisplayName("步骤数取基础与连接词推断的较大值，不重复计数")
        void should_takeMaxNotSum_when_connectorsPresent() {
            IntentResult intent = new IntentResult(IntentType.ROOT_CAUSE, 0.85, IntentResult.SOURCE_L1_REGEX, null);

            // ROOT_CAUSE 基础 3 步，1 个连接词 → max(3, 1+1) = 3（旧的累加逻辑会算成 4）
            TaskComplexity complexity = analyzer.analyze("为什么服务变慢然后定位原因", intent);

            assertThat(complexity.estimatedSteps()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("探索性判定收紧")
    class ExploratoryRefinement {

        @Test
        @DisplayName("分析关键词不再触发探索性判定")
        void should_notMarkExploratory_when_onlyAnalyzeKeyword() {
            IntentResult intent = new IntentResult(IntentType.ANALYZE_ALERT, 0.85, IntentResult.SOURCE_L1_REGEX, null);

            TaskComplexity complexity = analyzer.analyze("分析当前告警", intent);

            assertThat(complexity.isExploratory()).isFalse();
        }

        @Test
        @DisplayName("EXECUTE_OPERATION 意图含排查关键词也不判探索性")
        void should_notMarkExploratory_when_executeOperationWithDiagnoseKeyword() {
            IntentResult intent = new IntentResult(IntentType.EXECUTE_OPERATION, 0.9, IntentResult.SOURCE_L1_REGEX, null);

            // 含"排查"但意图为执行操作：按结构化步骤处理 → PLAN_AND_SOLVE
            TaskComplexity complexity = analyzer.analyze("排查后重启服务然后清理缓存", intent);

            assertThat(complexity.isExploratory()).isFalse();
            assertThat(complexity.suggestedMode()).isEqualTo(AgentMode.PLAN_AND_SOLVE);
        }
    }

    @Nested
    @DisplayName("实时性决胜")
    class RealTimeTiebreak {

        @Test
        @DisplayName("多步骤任务含实时关键词时优先 REACT")
        void should_returnReAct_when_multiStepButRealTime() {
            IntentResult intent = new IntentResult(IntentType.EXECUTE_OPERATION, 0.9, IntentResult.SOURCE_L1_REGEX, null);

            // 3 动作 + 2 连接词 = 3 步且有依赖，但"紧急"触发实时性决胜 → REACT
            TaskComplexity complexity = analyzer.analyze("紧急重启服务然后清理缓存接着扩容集群", intent);

            assertThat(complexity.realTimeRequired()).isTrue();
            assertThat(complexity.estimatedSteps()).isGreaterThanOrEqualTo(3);
            assertThat(complexity.suggestedMode()).isEqualTo(AgentMode.REACT);
        }
    }

    @Nested
    @DisplayName("异常处理")
    class ExceptionHandling {

        @Test
        @DisplayName("输入为 null 时抛出异常")
        void should_throwIllegalArg_when_inputNull() {
            IntentResult intent = new IntentResult(IntentType.QUERY_METRIC, 0.85, IntentResult.SOURCE_L1_REGEX, null);

            assertThatThrownBy(() -> analyzer.analyze(null, intent))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("用户输入");
        }

        @Test
        @DisplayName("输入为空白时抛出异常")
        void should_throwIllegalArg_when_inputBlank() {
            IntentResult intent = new IntentResult(IntentType.QUERY_METRIC, 0.85, IntentResult.SOURCE_L1_REGEX, null);

            assertThatThrownBy(() -> analyzer.analyze("   ", intent))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("意图结果为 null 时抛出异常")
        void should_throwIllegalArg_when_intentNull() {
            assertThatThrownBy(() -> analyzer.analyze("查询 CPU", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("意图识别结果");
        }
    }
}
