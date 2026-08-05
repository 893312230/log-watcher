package com.smartops.agent.plan;

import com.smartops.agent.tools.PrometheusTools;
import com.smartops.common.enums.AgentMode;
import com.smartops.common.exception.AgentException;
import com.smartops.common.model.AgentExecutionResult;
import com.smartops.common.model.ExecutionPlan;
import com.smartops.infrastructure.chat.ChatService;
import com.smartops.infrastructure.memory.WorkingMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Plan-and-Solve 执行器。
 *
 * <p>对应 agent.md 阶段二任务8 的核心编排组件。采用"先规划后执行"模式：
 * 先由 {@link PlanGenerator} 生成多步骤计划，再逐步执行每个步骤，
 * 失败时针对剩余工作重新规划并续接执行，全部完成后生成最终总结。</p>
 *
 * <p><b>执行流程</b>：
 * <ol>
 *   <li>调用 {@link PlanGenerator#generate} 生成执行计划</li>
 *   <li>校验计划有效性（步骤非空）</li>
 *   <li>逐步执行：每个步骤通过 {@link ChatService#chatWithTools(String, String, Object...)}
 *       携带 {@link PrometheusTools} 调用 LLM，步骤可直接查询运维指标</li>
 *   <li>步骤失败时，调用 {@link PlanGenerator#replan} 只为<b>剩余工作</b>重新规划，
 *       已成功的步骤不重复执行；重规划最多 {@value #MAX_REPLAN_ATTEMPTS} 次</li>
 *   <li>全部步骤成功后，调用 LLM 生成最终总结</li>
 *   <li>返回 {@link AgentExecutionResult}（含总结、模式、成功步骤数、步骤记录）</li>
 * </ol></p>
 *
 * <p><b>重试策略</b>：单个步骤执行抛异常时，保留已完成步骤的执行记录，
 * 基于失败原因重新规划剩余步骤并续接执行。重规划次数不超过
 * {@value #MAX_REPLAN_ATTEMPTS}，超过后返回失败结果。相比"从头重建整个计划"，
 * 续接式重规划避免了重复执行已成功的步骤（尤其对带副作用的运维操作更安全）。</p>
 *
 * <p><b>与 ReAct 模式的区别</b>：Plan-and-Solve 适合步骤数多、依赖关系复杂的
 * 结构化任务（如多步运维操作编排）；ReAct 适合实时性要求高、探索性强的任务。
 * 详见规划文档 1.3 节。</p>
 *
 * <p>线程安全：依赖组件均线程安全，本组件无状态，组件单例，线程安全。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Component
public class PlanAndSolveExecutor {

    private static final Logger log = LoggerFactory.getLogger(PlanAndSolveExecutor.class);

    /** 最大重新规划次数：步骤执行失败后最多针对剩余工作重新规划 2 次。 */
    static final int MAX_REPLAN_ATTEMPTS = 2;

    /** 总结系统提示词：指导 LLM 综合步骤结果生成最终回答。 */
    private static final String SUMMARY_SYSTEM_PROMPT = """
            你是一个运维总结助手。请根据以下执行步骤的结果，生成简洁的最终总结回答。

            要求：
            - 综合所有步骤的执行结果
            - 突出关键发现和结论
            - 语言简洁明了，直接回应用户原始请求
            """;

    private final PlanGenerator planGenerator;
    private final ChatService chatService;
    private final PrometheusTools prometheusTools;

    /** 工作记忆（可选）：执行过程中写入步骤记录，任务结束清理。 */
    private final ObjectProvider<WorkingMemory> workingMemoryProvider;

    /** 工作记忆条目 key：Plan-and-Solve 步骤记录。 */
    static final String WORKING_MEMORY_KEY = "plan.steps";

    /**
     * 构造 Plan-and-Solve 执行器。
     *
     * @param planGenerator         计划生成器，负责将用户请求分解为步骤、针对剩余工作重新规划
     * @param chatService           LLM 对话服务，用于步骤执行和总结生成
     * @param prometheusTools       Prometheus 指标查询工具，步骤执行时供 LLM 调用
     * @param workingMemoryProvider 工作记忆提供者（可选 Bean，ADR-014 工作记忆层；
     *                              smartops.memory.working.enabled=false 时跳过读写）
     */
    public PlanAndSolveExecutor(PlanGenerator planGenerator, ChatService chatService,
                                PrometheusTools prometheusTools,
                                ObjectProvider<WorkingMemory> workingMemoryProvider) {
        this.planGenerator = planGenerator;
        this.chatService = chatService;
        this.prometheusTools = prometheusTools;
        this.workingMemoryProvider = workingMemoryProvider;
    }

    /**
     * 执行 Plan-and-Solve 流程。
     *
     * <p>完整的计划生成 → 步骤执行 → 续接式重新规划 → 总结流程。步骤执行失败时
     * 针对剩余工作重新规划，最多 {@value #MAX_REPLAN_ATTEMPTS} 次；
     * 已成功的步骤不会重复执行。</p>
     *
     * @param userInput      用户输入文本，不能为 null 或空白
     * @param conversationId 会话 ID，用于日志追踪（可为 null）
     * @return 执行结果（成功含总结，失败含错误信息），模式始终为 PLAN_AND_SOLVE；
     *         iterations 为实际执行成功的步骤数
     * @throws IllegalArgumentException 当用户输入为 null 或空白时
     */
    public AgentExecutionResult execute(String userInput, String conversationId) {
        if (userInput == null || userInput.isBlank()) {
            throw new IllegalArgumentException("用户输入不能为 null 或空白");
        }

        log.info("开始 Plan-and-Solve 执行: conversationId={}", conversationId);
        try {
            return doExecute(userInput, conversationId);
        } finally {
            // 工作记忆生命周期为单次任务，结束即清理（ADR-014）
            clearWorkingMemory(conversationId);
        }
    }

    /**
     * 执行计划生成 → 步骤执行 → 续接式重规划 → 总结的完整流程。
     *
     * @param userInput      用户输入文本（已校验非空）
     * @param conversationId 会话 ID
     * @return 执行结果
     */
    private AgentExecutionResult doExecute(String userInput, String conversationId) {
        List<String> stepRecords = new ArrayList<>();

        // 1. 生成初始计划
        ExecutionPlan remaining;
        try {
            remaining = planGenerator.generate(userInput);
        } catch (AgentException e) {
            log.error("计划生成失败", e);
            return AgentExecutionResult.failure(AgentMode.PLAN_AND_SOLVE, 0, stepRecords,
                    "计划生成失败: " + e.getMessage());
        }

        // 2. 校验计划有效性（步骤非空）
        // 注：ExecutionPlan 记录类构造时已保证 steps 非 null，此处只校验是否为空集合
        if (remaining.steps().isEmpty()) {
            log.error("生成的计划为空");
            return AgentExecutionResult.failure(AgentMode.PLAN_AND_SOLVE, 0, stepRecords,
                    "生成的计划为空");
        }

        // 3. 执行计划，失败时针对剩余工作重新规划并续接执行
        int totalSucceeded = 0;
        int replanCount = 0;
        while (true) {
            StepBatchResult batch = executeSteps(remaining, conversationId, stepRecords);
            totalSucceeded += batch.succeededCount();
            saveWorkingMemory(conversationId, stepRecords);

            if (batch.failureReason() == null) {
                // 全部步骤成功，生成最终总结
                try {
                    String summary = generateSummary(stepRecords);
                    log.info("Plan-and-Solve 执行成功: conversationId={}, succeededSteps={}, replans={}",
                            conversationId, totalSucceeded, replanCount);
                    return AgentExecutionResult.success(summary, AgentMode.PLAN_AND_SOLVE,
                            totalSucceeded, stepRecords);
                } catch (AgentException e) {
                    log.error("总结生成失败", e);
                    return AgentExecutionResult.failure(AgentMode.PLAN_AND_SOLVE,
                            totalSucceeded, stepRecords,
                            "总结生成失败: " + e.getMessage());
                }
            }

            // 步骤执行失败，判断是否还能重新规划
            if (replanCount >= MAX_REPLAN_ATTEMPTS) {
                log.error("Plan-and-Solve 执行失败：重新规划次数超过上限 {}", MAX_REPLAN_ATTEMPTS);
                return AgentExecutionResult.failure(AgentMode.PLAN_AND_SOLVE,
                        totalSucceeded, stepRecords,
                        "重试次数超过上限: " + MAX_REPLAN_ATTEMPTS);
            }
            replanCount++;
            log.warn("步骤执行失败，针对剩余工作重新规划: replan={}/{}, reason={}",
                    replanCount, MAX_REPLAN_ATTEMPTS, batch.failureReason());

            try {
                remaining = planGenerator.replan(userInput, List.copyOf(stepRecords), batch.failureReason());
            } catch (AgentException e) {
                log.error("重新规划失败", e);
                return AgentExecutionResult.failure(AgentMode.PLAN_AND_SOLVE,
                        totalSucceeded, stepRecords, "重新规划失败: " + e.getMessage());
            }
            if (remaining.steps().isEmpty()) {
                log.error("重新生成的计划为空");
                return AgentExecutionResult.failure(AgentMode.PLAN_AND_SOLVE,
                        totalSucceeded, stepRecords, "重新生成的计划为空");
            }
        }
    }

    /**
     * 执行计划中的所有步骤。
     *
     * <p>逐步调用 {@link ChatService#chatWithTools(String, String, Object...)}
     * 执行每个步骤的动作（携带 Prometheus 工具，步骤可直接查询运维指标），
     * 将结果记录到 stepRecords。遇到异常时立即停止并返回失败原因，
     * 不继续执行后续步骤（避免基于错误中间结果继续操作）。</p>
     *
     * @param plan           执行计划
     * @param conversationId 会话 ID，步骤执行时读写该会话的短期记忆
     * @param stepRecords    步骤记录列表（输出参数，每条记录描述一个步骤的执行情况）
     * @return 批次结果：本批执行成功的步骤数与失败原因（null 表示全部成功）
     */
    private StepBatchResult executeSteps(ExecutionPlan plan, String conversationId, List<String> stepRecords) {
        int succeeded = 0;
        for (ExecutionPlan.PlanStep step : plan.steps()) {
            try {
                // 会话级带工具调用：步骤执行读写该会话的短期记忆，且可查询 Prometheus 指标
                String result = chatService.chatWithTools(conversationId, step.action(), prometheusTools);
                stepRecords.add(formatStepSuccess(step, result));
                succeeded++;
                log.debug("步骤 {} 执行成功: {}", step.index(), step.description());
            } catch (AgentException e) {
                stepRecords.add(formatStepFailure(step, e));
                log.warn("步骤 {} 执行失败: {}", step.index(), e.getMessage());
                return new StepBatchResult(succeeded, e.getMessage());
            }
        }
        return new StepBatchResult(succeeded, null);
    }

    /**
     * 生成最终总结。
     *
     * <p>将所有步骤的执行记录作为上下文，调用 LLM 生成综合性的最终回答。
     * 使用专门的总结系统提示词，确保输出简洁且直接回应用户原始请求。</p>
     *
     * @param stepRecords 步骤执行记录
     * @return LLM 生成的总结
     */
    private String generateSummary(List<String> stepRecords) {
        String summaryInput = "执行步骤结果：\n" + String.join("\n", stepRecords);
        // 无状态调用：总结是一次性元调用，不写入会话记忆
        return chatService.chatWithSystemPrompt(SUMMARY_SYSTEM_PROMPT, summaryInput);
    }

    /**
     * 格式化步骤执行成功记录。
     *
     * @param step   步骤
     * @param result 执行结果（可能为 null，此时显示为空）
     * @return 格式化的记录字符串
     */
    private String formatStepSuccess(ExecutionPlan.PlanStep step, String result) {
        return "步骤" + (step.index() + 1) + " [" + step.description() + "]: "
                + (result != null ? result : "");
    }

    /**
     * 格式化步骤执行失败记录。
     *
     * @param step 步骤
     * @param e    异常
     * @return 格式化的记录字符串
     */
    private String formatStepFailure(ExecutionPlan.PlanStep step, Exception e) {
        return "步骤" + (step.index() + 1) + " [" + step.description() + "] 失败: "
                + e.getMessage();
    }

    /**
     * 单批步骤执行结果。
     *
     * @param succeededCount 本批执行成功的步骤数
     * @param failureReason  失败原因（异常消息），null 表示全部步骤执行成功
     */
    private record StepBatchResult(int succeededCount, String failureReason) {
    }

    /**
     * 将当前步骤记录写入工作记忆；会话 ID 为 null 或工作记忆未启用时跳过。
     *
     * @param conversationId 会话 ID
     * @param stepRecords    步骤执行记录
     */
    private void saveWorkingMemory(String conversationId, List<String> stepRecords) {
        if (conversationId == null) {
            return;
        }
        WorkingMemory workingMemory = workingMemoryProvider.getIfAvailable();
        if (workingMemory != null) {
            workingMemory.put(conversationId, WORKING_MEMORY_KEY, String.join("\n", stepRecords));
        }
    }

    /**
     * 清理会话的工作记忆；会话 ID 为 null 或工作记忆未启用时跳过。
     *
     * @param conversationId 会话 ID
     */
    private void clearWorkingMemory(String conversationId) {
        if (conversationId == null) {
            return;
        }
        WorkingMemory workingMemory = workingMemoryProvider.getIfAvailable();
        if (workingMemory != null) {
            workingMemory.clear(conversationId);
        }
    }
}
