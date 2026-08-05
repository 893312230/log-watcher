package com.smartops.common.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ExecutionPlan} 单元测试。
 *
 * <p>验证 Plan-and-Solve 执行计划的构造、校验、不可变性、工厂方法等契约，
 * 同时覆盖内部 {@link ExecutionPlan.PlanStep} 的行为。
 * 对应 agent.md 阶段三 Plan-and-Solve 执行器相关组件。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class ExecutionPlanTest {

    @Nested
    @DisplayName("工厂方法与转换")
    class FactoryMethods {

        @Test
        @DisplayName("of 创建未校验计划，isValidated=false")
        void should_createUnvalidatedPlan_when_ofCalled() {
            List<ExecutionPlan.PlanStep> steps = List.of(
                    ExecutionPlan.PlanStep.of(0, "查询指标", "调用 Prometheus"),
                    ExecutionPlan.PlanStep.of(1, "分析结果", "调用 LLM 分析")
            );

            ExecutionPlan plan = ExecutionPlan.of("诊断 CPU 高", steps);

            assertThat(plan.goal()).isEqualTo("诊断 CPU 高");
            assertThat(plan.steps()).hasSize(2);
            assertThat(plan.isValidated()).isFalse();
        }

        @Test
        @DisplayName("validated 返回已校验副本，isValidated=true")
        void should_returnValidatedCopy_when_validatedCalled() {
            List<ExecutionPlan.PlanStep> steps = List.of(
                    ExecutionPlan.PlanStep.of(0, "查询", "工具调用")
            );
            ExecutionPlan plan = ExecutionPlan.of("目标", steps);

            ExecutionPlan validated = plan.validated();

            assertThat(validated.isValidated()).isTrue();
            assertThat(validated.goal()).isEqualTo(plan.goal());
            assertThat(validated.steps()).containsExactlyElementsOf(plan.steps());
            // 原计划保持未校验状态，证明 validated 返回新实例
            assertThat(plan.isValidated()).isFalse();
        }

        @Test
        @DisplayName("validated 保留原计划的全部步骤内容")
        void should_preserveSteps_when_validatedCalled() {
            List<ExecutionPlan.PlanStep> steps = List.of(
                    ExecutionPlan.PlanStep.of(0, "步骤1", "动作1"),
                    ExecutionPlan.PlanStep.of(1, "步骤2", "动作2"),
                    ExecutionPlan.PlanStep.of(2, "步骤3", "动作3")
            );
            ExecutionPlan plan = ExecutionPlan.of("目标", steps);

            ExecutionPlan validated = plan.validated();

            assertThat(validated.steps()).hasSize(3);
            assertThat(validated.stepCount()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("stepCount 方法")
    class StepCount {

        @Test
        @DisplayName("stepCount 返回正确的步骤数")
        void should_returnCorrectCount_when_stepsProvided() {
            List<ExecutionPlan.PlanStep> steps = List.of(
                    ExecutionPlan.PlanStep.of(0, "a", "b"),
                    ExecutionPlan.PlanStep.of(1, "c", "d"),
                    ExecutionPlan.PlanStep.of(2, "e", "f")
            );
            ExecutionPlan plan = ExecutionPlan.of("目标", steps);

            assertThat(plan.stepCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("空步骤列表时 stepCount 返回 0")
        void should_returnZero_when_stepsEmpty() {
            ExecutionPlan plan = ExecutionPlan.of("目标", List.of());

            assertThat(plan.stepCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("构造与校验")
    class Construction {

        @Test
        @DisplayName("goal 为 null 时抛出 NullPointerException")
        void should_throwNpe_when_goalIsNull() {
            assertThatThrownBy(() -> new ExecutionPlan(null, List.of(), false))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("计划目标");
        }

        @Test
        @DisplayName("steps 为 null 时抛出 NullPointerException")
        void should_throwNpe_when_stepsIsNull() {
            assertThatThrownBy(() -> new ExecutionPlan("目标", null, false))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("步骤列表");
        }

        @Test
        @DisplayName("空步骤列表的边界情况构造成功")
        void should_construct_when_stepsEmpty() {
            ExecutionPlan plan = new ExecutionPlan("目标", List.of(), false);

            assertThat(plan.steps()).isNotNull().isEmpty();
            assertThat(plan.stepCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("不可变性")
    class Immutability {

        @Test
        @DisplayName("返回的 steps 列表不可修改")
        void should_returnUnmodifiableList_when_stepsAccessed() {
            ExecutionPlan plan = ExecutionPlan.of("目标",
                    List.of(ExecutionPlan.PlanStep.of(0, "步骤", "动作")));

            assertThatThrownBy(() -> plan.steps().add(ExecutionPlan.PlanStep.of(1, "x", "y")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("原始 steps 列表修改不影响 ExecutionPlan")
        void should_notBeAffected_when_originalStepsModified() {
            List<ExecutionPlan.PlanStep> mutable = new ArrayList<>(
                    List.of(ExecutionPlan.PlanStep.of(0, "步骤", "动作"))
            );
            ExecutionPlan plan = ExecutionPlan.of("目标", mutable);

            mutable.add(ExecutionPlan.PlanStep.of(1, "新增", "动作"));

            assertThat(plan.steps()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("PlanStep 内部 record")
    class PlanStepTests {

        @Test
        @DisplayName("of 创建步骤，字段正确")
        void should_createStep_when_ofCalled() {
            ExecutionPlan.PlanStep step = ExecutionPlan.PlanStep.of(0, "查询 CPU", "调用工具");

            assertThat(step.index()).isEqualTo(0);
            assertThat(step.description()).isEqualTo("查询 CPU");
            assertThat(step.action()).isEqualTo("调用工具");
        }

        @Test
        @DisplayName("description 为 null 时抛出 NullPointerException")
        void should_throwNpe_when_descriptionIsNull() {
            assertThatThrownBy(() -> new ExecutionPlan.PlanStep(0, null, "动作"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("步骤描述");
        }

        @Test
        @DisplayName("action 为 null 时抛出 NullPointerException")
        void should_throwNpe_when_actionIsNull() {
            assertThatThrownBy(() -> new ExecutionPlan.PlanStep(0, "描述", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("步骤动作");
        }

        @Test
        @DisplayName("index 为 0 的边界情况构造成功")
        void should_construct_when_indexZero() {
            ExecutionPlan.PlanStep step = ExecutionPlan.PlanStep.of(0, "描述", "动作");

            assertThat(step.index()).isEqualTo(0);
        }
    }
}
