package com.smartops.common.model;

import com.smartops.common.enums.AgentMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link TaskComplexity} 单元测试。
 *
 * <p>验证任务复杂度分析结果的构造、校验、工厂方法等契约。
 * 对应 agent.md 阶段二任务复杂度分析器。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class TaskComplexityTest {

    @Nested
    @DisplayName("构造与校验")
    class Construction {

        @Test
        @DisplayName("合法参数构造成功")
        void should_construct_when_validParams() {
            TaskComplexity complexity = new TaskComplexity(
                    3, true, false, false,
                    AgentMode.PLAN_AND_SOLVE,
                    List.of("查询指标", "分析趋势", "生成报告")
            );

            assertThat(complexity.estimatedSteps()).isEqualTo(3);
            assertThat(complexity.hasDependencies()).isTrue();
            assertThat(complexity.realTimeRequired()).isFalse();
            assertThat(complexity.isExploratory()).isFalse();
            assertThat(complexity.suggestedMode()).isEqualTo(AgentMode.PLAN_AND_SOLVE);
            assertThat(complexity.steps()).hasSize(3);
        }

        @Test
        @DisplayName("steps 为 null 时返回空列表")
        void should_returnEmptyList_when_stepsNull() {
            TaskComplexity complexity = new TaskComplexity(
                    1, false, true, false, AgentMode.REACT, null
            );

            assertThat(complexity.steps()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("建议模式为 null 时抛出 NullPointerException")
        void should_throwNpe_when_suggestedModeIsNull() {
            assertThatThrownBy(() -> new TaskComplexity(1, false, false, false, null, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("执行模式");
        }

        @Test
        @DisplayName("步骤数小于 1 时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_stepsLessThanOne() {
            assertThatThrownBy(() -> new TaskComplexity(0, false, false, false, AgentMode.REACT, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("1-10");
        }

        @Test
        @DisplayName("步骤数大于 10 时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_stepsGreaterThanTen() {
            assertThatThrownBy(() -> new TaskComplexity(11, false, false, false, AgentMode.REACT, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("步骤数为边界值 1 和 10 时构造成功")
        void should_construct_when_stepsAtBoundaries() {
            assertThat(new TaskComplexity(1, false, false, false, AgentMode.REACT, null)).isNotNull();
            assertThat(new TaskComplexity(10, true, false, false, AgentMode.PLAN_AND_SOLVE, null)).isNotNull();
        }
    }

    @Nested
    @DisplayName("不可变性")
    class Immutability {

        @Test
        @DisplayName("返回的 steps 列表不可修改")
        void should_returnUnmodifiableList_when_stepsAccessed() {
            TaskComplexity complexity = new TaskComplexity(
                    2, false, false, false, AgentMode.REACT, List.of("步骤1")
            );

            assertThatThrownBy(() -> complexity.steps().add("步骤2"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("isSimple 与 isComplex")
    class SimpleAndComplex {

        @Test
        @DisplayName("步骤数 1 且无依赖为简单任务")
        void should_beSimple_when_oneStepNoDependency() {
            TaskComplexity complexity = new TaskComplexity(1, false, false, false, AgentMode.REACT, null);

            assertThat(complexity.isSimple()).isTrue();
            assertThat(complexity.isComplex()).isFalse();
        }

        @Test
        @DisplayName("步骤数 2 且无依赖为简单任务")
        void should_beSimple_when_twoStepsNoDependency() {
            TaskComplexity complexity = new TaskComplexity(2, false, false, false, AgentMode.REACT, null);

            assertThat(complexity.isSimple()).isTrue();
        }

        @Test
        @DisplayName("步骤数 2 但有依赖为复杂任务")
        void should_beComplex_when_hasDependency() {
            TaskComplexity complexity = new TaskComplexity(2, true, false, false, AgentMode.PLAN_AND_SOLVE, null);

            assertThat(complexity.isSimple()).isFalse();
            assertThat(complexity.isComplex()).isTrue();
        }

        @Test
        @DisplayName("步骤数 3 为复杂任务")
        void should_beComplex_when_threeSteps() {
            TaskComplexity complexity = new TaskComplexity(3, false, false, false, AgentMode.PLAN_AND_SOLVE, null);

            assertThat(complexity.isComplex()).isTrue();
        }
    }

    @Nested
    @DisplayName("工厂方法")
    class FactoryMethods {

        @Test
        @DisplayName("simple 工厂创建单步骤 ReAct 任务")
        void should_createSimpleTask_when_simpleCalled() {
            TaskComplexity complexity = TaskComplexity.simple(true);

            assertThat(complexity.estimatedSteps()).isEqualTo(1);
            assertThat(complexity.hasDependencies()).isFalse();
            assertThat(complexity.realTimeRequired()).isTrue();
            assertThat(complexity.suggestedMode()).isEqualTo(AgentMode.REACT);
            assertThat(complexity.steps()).hasSize(1);
        }

        @Test
        @DisplayName("complex 工厂创建多步骤 Plan-and-Solve 任务")
        void should_createComplexTask_when_complexCalled() {
            List<String> steps = List.of("查询", "分析", "修复");
            TaskComplexity complexity = TaskComplexity.complex(3, steps);

            assertThat(complexity.estimatedSteps()).isEqualTo(3);
            assertThat(complexity.hasDependencies()).isTrue();
            assertThat(complexity.suggestedMode()).isEqualTo(AgentMode.PLAN_AND_SOLVE);
            assertThat(complexity.steps()).containsExactly("查询", "分析", "修复");
        }
    }
}
