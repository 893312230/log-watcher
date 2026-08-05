package com.smartops.common.model;

import com.smartops.common.enums.AgentMode;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 任务复杂度分析结果。
 *
 * <p>由任务复杂度分析器（TaskAnalyzer）产出，包含预估步骤数、依赖关系、
 * 实时性要求等维度，供路由决策引擎在 ReAct 与 Plan-and-Solve 之间动态选择。</p>
 *
 * <p>路由策略参考 agent.md 第三章 3.3 节"动态路由"：
 * <ul>
 *   <li>步骤数少（≤2）、实时性高 → ReAct</li>
 *   <li>步骤数多（≥3）、有依赖 → Plan-and-Solve</li>
 *   <li>探索性强的根因分析 → ReAct</li>
 *   <li>结构化的运维流程编排 → Plan-and-Solve</li>
 * </ul></p>
 *
 * <p>线程安全：字段不可变，steps 为不可变 List。</p>
 *
 * @author smartops
 * @since 1.0.0
 *
 * @param estimatedSteps      预估步骤数（1-10）
 * @param hasDependencies     步骤间是否存在依赖关系
 * @param realTimeRequired    是否需要实时反馈
 * @param isExploratory       是否为探索性任务（如根因分析）
 * @param suggestedMode       根据复杂度建议的执行模式
 * @param steps               预估的步骤摘要列表，可能为空
 */
public record TaskComplexity(
        int estimatedSteps,
        boolean hasDependencies,
        boolean realTimeRequired,
        boolean isExploratory,
        AgentMode suggestedMode,
        List<String> steps
) {

    /**
     * 紧凑构造器：对 steps 做防御性拷贝，校验步骤数范围。
     *
     * @param estimatedSteps      预估步骤数
     * @param hasDependencies     是否有依赖
     * @param realTimeRequired    是否需要实时性
     * @param isExploratory       是否探索性
     * @param suggestedMode       建议模式
     * @param steps               步骤摘要
     */
    public TaskComplexity {
        Objects.requireNonNull(suggestedMode, "建议的执行模式不能为 null");
        if (estimatedSteps < 1 || estimatedSteps > 10) {
            throw new IllegalArgumentException("预估步骤数必须在 1-10 之间，实际: " + estimatedSteps);
        }
        steps = steps == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(List.copyOf(steps));
    }

    /**
     * 判断任务是否为简单任务（步骤数 ≤ 2 且无依赖）。
     *
     * @return 若为简单任务返回 true
     */
    public boolean isSimple() {
        return estimatedSteps <= 2 && !hasDependencies;
    }

    /**
     * 判断任务是否为复杂任务（步骤数 ≥ 3 或有依赖）。
     *
     * @return 若为复杂任务返回 true
     */
    public boolean isComplex() {
        return estimatedSteps >= 3 || hasDependencies;
    }

    /**
     * 创建一个最简单的单步骤任务复杂度（用于查询指标等简单场景）。
     *
     * @param realTimeRequired 是否需要实时性
     * @return 单步骤任务复杂度
     */
    public static TaskComplexity simple(boolean realTimeRequired) {
        return new TaskComplexity(
                1, false, realTimeRequired, false,
                AgentMode.REACT,
                List.of("查询并返回结果")
        );
    }

    /**
     * 创建一个复杂的多步骤任务复杂度（用于运维流程编排等场景）。
     *
     * @param stepCount  步骤数
     * @param steps      步骤摘要
     * @return 多步骤任务复杂度
     */
    public static TaskComplexity complex(int stepCount, List<String> steps) {
        return new TaskComplexity(
                stepCount, true, false, false,
                AgentMode.PLAN_AND_SOLVE,
                steps
        );
    }
}
