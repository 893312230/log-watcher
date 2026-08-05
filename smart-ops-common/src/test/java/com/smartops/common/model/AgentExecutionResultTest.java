package com.smartops.common.model;

import com.smartops.common.enums.AgentMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AgentExecutionResult} 单元测试。
 *
 * <p>验证 Agent 执行结果的构造、校验、不可变性与工厂方法等契约。
 * 对应 agent.md 阶段三 ReAct / Plan-and-Solve 执行器的统一返回格式。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class AgentExecutionResultTest {

    @Nested
    @DisplayName("工厂方法")
    class FactoryMethods {

        @Test
        @DisplayName("success 创建成功结果，字段全部正确")
        void should_createSuccessResult_when_successCalled() {
            List<String> steps = List.of("思考", "查询指标", "总结");

            AgentExecutionResult result = AgentExecutionResult.success(
                    "CPU 使用率正常", AgentMode.REACT, 3, steps
            );

            assertThat(result.answer()).isEqualTo("CPU 使用率正常");
            assertThat(result.mode()).isEqualTo(AgentMode.REACT);
            assertThat(result.iterations()).isEqualTo(3);
            assertThat(result.steps()).containsExactly("思考", "查询指标", "总结");
            assertThat(result.success()).isTrue();
            assertThat(result.errorMessage()).isNull();
        }

        @Test
        @DisplayName("failure 创建失败结果，success=false 且 answer=null")
        void should_createFailureResult_when_failureCalled() {
            List<String> steps = List.of("思考", "调用工具失败");

            AgentExecutionResult result = AgentExecutionResult.failure(
                    AgentMode.PLAN_AND_SOLVE, 2, steps, "工具调用超时"
            );

            assertThat(result.answer()).isNull();
            assertThat(result.mode()).isEqualTo(AgentMode.PLAN_AND_SOLVE);
            assertThat(result.iterations()).isEqualTo(2);
            assertThat(result.steps()).containsExactly("思考", "调用工具失败");
            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).isNotEmpty().isEqualTo("工具调用超时");
        }

        @Test
        @DisplayName("success 工厂传入 null steps 时默认空列表")
        void should_defaultToEmptySteps_when_successCalledWithNullSteps() {
            AgentExecutionResult result = AgentExecutionResult.success(
                    "ok", AgentMode.REACT, 1, null
            );

            assertThat(result.steps()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("failure 工厂传入 null steps 时默认空列表")
        void should_defaultToEmptySteps_when_failureCalledWithNullSteps() {
            AgentExecutionResult result = AgentExecutionResult.failure(
                    AgentMode.REACT, 1, null, "错误"
            );

            assertThat(result.steps()).isNotNull().isEmpty();
        }
    }

    @Nested
    @DisplayName("构造与校验")
    class Construction {

        @Test
        @DisplayName("mode 为 null 时抛出 NullPointerException")
        void should_throwNpe_when_modeIsNull() {
            assertThatThrownBy(() ->
                    new AgentExecutionResult("answer", null, 1, List.of(), true, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("执行模式");
        }

        @Test
        @DisplayName("steps 有值时正确存储")
        void should_storeSteps_when_stepsProvided() {
            List<String> steps = List.of("step1", "step2", "step3");

            AgentExecutionResult result = new AgentExecutionResult(
                    "answer", AgentMode.REACT, 3, steps, true, null
            );

            assertThat(result.steps()).containsExactly("step1", "step2", "step3");
        }

        @Test
        @DisplayName("iterations 为 0 时构造成功")
        void should_construct_when_iterationsZero() {
            AgentExecutionResult result = new AgentExecutionResult(
                    "answer", AgentMode.REACT, 0, null, true, null
            );

            assertThat(result.iterations()).isEqualTo(0);
            assertThat(result.steps()).isEmpty();
        }

        @Test
        @DisplayName("steps 为 null 时默认为空列表")
        void should_defaultToEmptySteps_when_stepsNull() {
            AgentExecutionResult result = new AgentExecutionResult(
                    "answer", AgentMode.REACT, 1, null, true, null
            );

            assertThat(result.steps()).isNotNull().isEmpty();
        }
    }

    @Nested
    @DisplayName("不可变性")
    class Immutability {

        @Test
        @DisplayName("返回的 steps 列表不可修改")
        void should_returnUnmodifiableList_when_stepsAccessed() {
            AgentExecutionResult result = new AgentExecutionResult(
                    "answer", AgentMode.REACT, 1, List.of("step1"), true, null
            );

            assertThatThrownBy(() -> result.steps().add("step2"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("原始 steps 列表修改不影响 AgentExecutionResult")
        void should_notBeAffected_when_originalStepsModified() {
            List<String> mutable = new ArrayList<>(List.of("step1"));
            AgentExecutionResult result = new AgentExecutionResult(
                    "answer", AgentMode.REACT, 1, mutable, true, null
            );

            mutable.add("step2");

            assertThat(result.steps()).hasSize(1).containsExactly("step1");
        }
    }
}
