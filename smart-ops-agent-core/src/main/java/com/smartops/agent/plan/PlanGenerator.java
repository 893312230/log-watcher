package com.smartops.agent.plan;

import com.smartops.common.exception.AgentException;
import com.smartops.common.model.ExecutionPlan;
import com.smartops.common.model.ExecutionPlan.PlanStep;
import com.smartops.infrastructure.chat.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 计划生成器（Planner）。
 *
 * <p>对应 agent.md 阶段二任务8 的 Planner 组件。通过 LLM 将用户自然语言请求
 * 分解为结构化的多步骤执行计划，供 {@link PlanAndSolveExecutor} 逐步执行。</p>
 *
 * <p><b>工作流程</b>：
 * <ol>
 *   <li>构造计划生成系统提示词，要求 LLM 按固定格式返回计划</li>
 *   <li>调用 {@link ChatService#chat(String, String)} 发送请求</li>
 *   <li>解析 LLM 返回文本为 {@link ExecutionPlan}（每行一个步骤）</li>
 *   <li>解析失败时降级为单步默认计划，确保流程不中断</li>
 * </ol></p>
 *
 * <p><b>计划格式</b>（LLM 应返回的文本格式）：
 * <pre>
 * 1. 步骤描述 -> 具体动作
 * 2. 步骤描述 -> 具体动作
 * </pre></p>
 *
 * <p><b>降级策略</b>：当 LLM 调用失败或返回无法解析的文本时，
 * 返回只含单步"直接处理用户请求"的默认计划，将原始请求直接转发给 LLM 处理，
 * 保证 Plan-and-Solve 流程的健壮性。这与 agent.md 第九章 9.1 节
 * "Plan-and-Solve 计划质量"的应对策略一致：失败自动降级。</p>
 *
 * <p>线程安全：ChatService 线程安全，本组件无状态，组件单例，线程安全。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Component
public class PlanGenerator {

    private static final Logger log = LoggerFactory.getLogger(PlanGenerator.class);

    /**
     * 计划行解析正则：匹配 "序号. 描述 -> 动作" 格式。
     * <ul>
     *   <li>组1：序号（数字）</li>
     *   <li>组2：步骤描述</li>
     *   <li>组3：具体动作</li>
     * </ul>
     * 支持的序号分隔符：. 、 ) ）
     * 支持的箭头：-> →
     */
    private static final Pattern PLAN_LINE_PATTERN = Pattern.compile(
            "^\\s*(\\d+)\\s*[.、)）]\\s*(.+?)\\s*(?:->|→)\\s*(.+)$"
    );

    /** 计划生成系统提示词：指导 LLM 按格式输出计划。 */
    private static final String SYSTEM_PROMPT = """
            你是一个运维任务规划器。请将用户请求分解为可执行的步骤列表。

            要求：
            - 每行一个步骤，不要输出多余内容
            - 格式：序号. 描述 -> 动作
            - 序号从 1 开始递增
            - 描述简明说明该步骤的目的
            - 动作是具体要执行的操作指令

            示例：
            1. 查询当前CPU使用率 -> 查询CPU当前使用率指标
            2. 分析最近一小时趋势 -> 分析CPU最近一小时的指标趋势
            3. 生成分析报告 -> 综合查询结果生成CPU分析报告
            """;

    /** 重新规划系统提示词：基于已完成工作与失败原因，只为剩余工作生成步骤。 */
    private static final String REPLAN_SYSTEM_PROMPT = """
            你是一个运维任务规划器。用户的多步骤任务在执行中遇到失败，
            请基于已完成的工作，只为【剩余工作】重新生成步骤列表。

            要求：
            - 每行一个步骤，不要输出多余内容
            - 格式：序号. 描述 -> 动作
            - 序号从 1 开始递增
            - 不要重复已完成的步骤
            - 针对失败原因调整后续步骤（如更换查询方式、拆分复杂步骤）
            """;

    /**
     * 默认计划步骤描述（LLM 解析失败时使用的兜底步骤）。
     * 显式标注"计划生成降级"，使降级在步骤记录与最终总结输入中可见，
     * 不再是静默降级。
     */
    static final String DEFAULT_STEP_DESCRIPTION = "直接处理用户请求（计划生成降级）";

    private final ChatService chatService;

    /**
     * 构造计划生成器。
     *
     * @param chatService LLM 对话服务，用于发送计划生成请求
     */
    public PlanGenerator(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 根据用户输入生成执行计划。
     *
     * <p>调用 LLM 将用户请求分解为多步骤计划。当 LLM 调用失败或返回无法解析的
     * 文本时，降级返回单步默认计划（直接处理用户请求），保证流程不中断。</p>
     *
     * @param userInput 用户输入文本，不能为 null 或空白
     * @return 执行计划（至少含一个步骤）
     * @throws IllegalArgumentException 当用户输入为 null 或空白时
     */
    public ExecutionPlan generate(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            throw new IllegalArgumentException("用户输入不能为 null 或空白");
        }

        log.debug("开始生成执行计划: inputLength={}", userInput.length());

        try {
            // 无状态调用：计划生成是一次性元调用，不读写会话记忆
            String llmResponse = chatService.chatWithSystemPrompt(SYSTEM_PROMPT, userInput);
            ExecutionPlan plan = parsePlan(llmResponse);

            // 解析失败时，以 userInput 为动作生成默认计划，确保执行器能处理原始请求
            if (isDefaultPlan(plan)) {
                log.warn("LLM 响应无法解析为有效步骤，返回默认计划");
                return createDefaultPlan(userInput);
            }

            // 解析成功，用 userInput 作为计划目标
            return ExecutionPlan.of(userInput, plan.steps());
        } catch (AgentException e) {
            // 平台异常（LLM 调用失败）降级为默认计划；非平台异常向上传播
            log.error("LLM 计划生成失败，返回默认计划", e);
            return createDefaultPlan(userInput);
        }
    }

    /**
     * 针对剩余工作重新规划。
     *
     * <p>当 {@link PlanAndSolveExecutor} 的某个步骤执行失败时调用：
     * 将已完成步骤的执行记录与失败原因作为上下文，要求 LLM 只为
     * <b>剩余工作</b>生成新步骤，而不是从头重建整个计划——已成功的
     * 步骤不会重复执行。解析失败或 LLM 异常时同样降级为默认计划。</p>
     *
     * @param userInput            原始用户请求，不能为 null 或空白
     * @param completedStepRecords 已完成步骤的执行记录（含失败步骤记录），可为空
     * @param failureReason        触发重新规划的失败原因，可为 null
     * @return 剩余工作的执行计划（至少含一个步骤）
     * @throws IllegalArgumentException 当用户输入为 null 或空白时
     */
    public ExecutionPlan replan(String userInput, List<String> completedStepRecords, String failureReason) {
        if (userInput == null || userInput.isBlank()) {
            throw new IllegalArgumentException("用户输入不能为 null 或空白");
        }

        StringBuilder context = new StringBuilder("原始请求：").append(userInput).append('\n');
        if (completedStepRecords != null && !completedStepRecords.isEmpty()) {
            context.append("已完成的步骤与执行记录：\n")
                    .append(String.join("\n", completedStepRecords)).append('\n');
        }
        context.append("失败原因：").append(failureReason != null ? failureReason : "未知");

        log.debug("开始重新规划剩余步骤: completedRecords={}, reason={}",
                completedStepRecords != null ? completedStepRecords.size() : 0, failureReason);

        try {
            // 无状态调用：重新规划是一次性元调用，不读写会话记忆
            String llmResponse = chatService.chatWithSystemPrompt(REPLAN_SYSTEM_PROMPT, context.toString());
            ExecutionPlan plan = parsePlan(llmResponse);

            if (isDefaultPlan(plan)) {
                log.warn("重新规划的 LLM 响应无法解析为有效步骤，返回默认计划");
                return createDefaultPlan(userInput);
            }

            return ExecutionPlan.of(userInput, plan.steps());
        } catch (AgentException e) {
            log.error("LLM 重新规划失败，返回默认计划", e);
            return createDefaultPlan(userInput);
        }
    }

    /**
     * 解析 LLM 返回的计划文本为 {@link ExecutionPlan}。
     *
     * <p>解析规则：按行分割，匹配 "序号. 描述 -> 动作" 格式的行，
     * 提取序号、描述、动作构造 {@link PlanStep}。不匹配的行自动跳过。</p>
     *
     * <p><b>降级策略</b>：当响应为 null/空白或无任何匹配行时，
     * 返回只含单步"直接处理用户请求"的默认计划。</p>
     *
     * @param llmResponse LLM 返回的原始文本
     * @return 解析后的执行计划（至少含一个步骤）
     */
    ExecutionPlan parsePlan(String llmResponse) {
        if (llmResponse == null || llmResponse.isBlank()) {
            log.warn("LLM 返回空响应，使用默认计划");
            return createDefaultPlan("执行用户请求");
        }

        List<PlanStep> steps = new ArrayList<>();
        String[] lines = llmResponse.split("\\r?\\n");

        for (String line : lines) {
            Matcher matcher = PLAN_LINE_PATTERN.matcher(line);
            if (matcher.matches()) {
                String description = matcher.group(2).trim();
                String action = matcher.group(3).trim();
                // 使用 steps.size() 作为序号，保证从 0 开始连续递增
                // 而不依赖 LLM 返回的序号，避免序号不连续或重复
                steps.add(PlanStep.of(steps.size(), description, action));
            }
        }

        if (steps.isEmpty()) {
            log.warn("LLM 响应无法解析为计划步骤，使用默认计划: response={}", llmResponse);
            return createDefaultPlan("执行用户请求");
        }

        log.debug("计划解析成功: steps={}", steps.size());
        return ExecutionPlan.of("执行用户请求", steps);
    }

    /**
     * 判断是否为默认计划（单步且描述为默认描述）。
     *
     * <p>用于在 {@link #generate} 中区分 parsePlan 返回的默认计划与解析成功的计划，
     * 以便为默认计划设置 userInput 作为动作。</p>
     *
     * @param plan 待判断的计划
     * @return true 如果是默认计划
     */
    private boolean isDefaultPlan(ExecutionPlan plan) {
        return plan.stepCount() == 1
                && DEFAULT_STEP_DESCRIPTION.equals(plan.steps().get(0).description());
    }

    /**
     * 创建默认计划（单步：直接处理用户请求）。
     *
     * <p>默认计划的动作设为 goal，使执行器能将原始请求直接转发给 LLM。
     * 这确保了即使计划生成失败，Plan-and-Solve 流程仍能给出合理响应。</p>
     *
     * @param goal 计划目标，同时作为默认步骤的动作
     * @return 默认执行计划
     */
    private ExecutionPlan createDefaultPlan(String goal) {
        List<PlanStep> steps = new ArrayList<>();
        steps.add(PlanStep.of(0, DEFAULT_STEP_DESCRIPTION, goal));
        return ExecutionPlan.of(goal, steps);
    }
}
