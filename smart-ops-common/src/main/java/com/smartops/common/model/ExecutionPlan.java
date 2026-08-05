package com.smartops.common.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Plan-and-Solve 执行计划。
 *
 * <p>由 PlanGenerator 通过 LLM 生成，经 PlanValidator 校验后
 * 交由 PlanAndSolveExecutor 逐步执行。失败时由 Replanner 重新生成。</p>
 *
 * <p>线程安全：字段不可变，steps 为不可变 List。</p>
 *
 * @author smartops
 * @since 1.0.0
 *
 * @param goal      计划目标（用户意图摘要）
 * @param steps     执行步骤列表
 * @param isValidated 是否已通过校验
 */
public record ExecutionPlan(
        String goal,
        List<PlanStep> steps,
        boolean isValidated
) {

    /**
     * 紧凑构造器：对 steps 做防御性拷贝。
     *
     * @param goal       计划目标
     * @param steps      步骤列表
     * @param isValidated 是否已校验
     */
    public ExecutionPlan {
        Objects.requireNonNull(goal, "计划目标不能为 null");
        Objects.requireNonNull(steps, "步骤列表不能为 null");
        steps = Collections.unmodifiableList(List.copyOf(steps));
    }

    /**
     * 创建一个未校验的计划。
     *
     * @param goal  计划目标
     * @param steps 步骤列表
     * @return 未校验的计划
     */
    public static ExecutionPlan of(String goal, List<PlanStep> steps) {
        return new ExecutionPlan(goal, steps, false);
    }

    /**
     * 返回已校验版本的副本。
     *
     * @return 标记为已校验的新计划
     */
    public ExecutionPlan validated() {
        return new ExecutionPlan(goal, steps, true);
    }

    /**
     * 返回步骤数量。
     *
     * @return 步骤数
     */
    public int stepCount() {
        return steps.size();
    }

    /**
     * 计划中的单个执行步骤。
     *
     * @param index       步骤序号（从 0 开始）
     * @param description 步骤描述
     * @param action      要执行的动作（如工具调用描述）
     */
    public record PlanStep(
            int index,
            String description,
            String action
    ) {

        /**
         * 紧凑构造器：校验必要字段。
         *
         * @param index       序号
         * @param description 描述
         * @param action      动作
         */
        public PlanStep {
            Objects.requireNonNull(description, "步骤描述不能为 null");
            Objects.requireNonNull(action, "步骤动作不能为 null");
        }

        /**
         * 创建一个步骤。
         *
         * @param index       序号
         * @param description 描述
         * @param action      动作
         * @return 步骤实例
         */
        public static PlanStep of(int index, String description, String action) {
            return new PlanStep(index, description, action);
        }
    }
}
