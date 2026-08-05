package com.smartops.common.enums;

/**
 * Agent 执行模式枚举。
 *
 * <p>用于在动态路由阶段决定 Agent 采用何种工作流模式执行任务。
 * 选择依据见 agent.md 第三章 3.3 节"动态路由"。</p>
 *
 * <ul>
 *   <li>{@link #REACT} - 边想边做，适合实时告警分析、交互式查询</li>
 *   <li>{@link #PLAN_AND_SOLVE} - 先规划后执行，适合多步骤依赖、自动化运维流程编排</li>
 * </ul>
 *
 * 线程安全：枚举天然不可变，线程安全。
 *
 * @author smartops
 * @since 1.0.0
 */
public enum AgentMode {

    /**
     * ReAct 模式：Thought → Action → Observation 循环。
     * 适用于实时性要求高、探索性强的任务。
     */
    REACT,

    /**
     * Plan-and-Solve 模式：Planner → Executor → Replanner → Summarizer。
     * 适用于步骤数多、依赖关系复杂的结构化任务。
     */
    PLAN_AND_SOLVE,
}
