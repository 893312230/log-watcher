package com.smartops.agent.react;

import com.smartops.agent.tools.PrometheusTools;
import com.smartops.common.enums.AgentMode;
import com.smartops.common.exception.AgentException;
import com.smartops.common.model.AgentExecutionResult;
import com.smartops.infrastructure.advisor.ToolCallRoundGate;
import com.smartops.infrastructure.chat.ChatService;
import com.smartops.infrastructure.memory.WorkingMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * ReAct 模式执行器。
 *
 * <p>对应 agent.md 阶段二特性6（ReAct 执行器）。ReAct（Reasoning + Acting）
 * 是一种边想边做的 Agent 执行模式，适用于实时性要求高、探索性强的运维任务，
 * 如实时告警分析、交互式指标查询、根因定位等。</p>
 *
 * <p><b>执行流程</b>：
 * <ol>
 *   <li>接收用户输入与会话 ID</li>
 *   <li>通过 {@link ChatService#chatWithTools(String, Object...)} 将用户输入
 *       与 {@link PrometheusTools} 一起发送给 LLM</li>
 *   <li>LLM 内部自动完成 Thought → Action → Observation 循环
 *       （由 Spring AI 的 Tool Calling 机制处理，无需手动编排）</li>
 *   <li>收集最终答案与执行步骤，封装为 {@link AgentExecutionResult} 返回</li>
 * </ol></p>
 *
 * <p><b>迭代次数说明</b>：{@code maxIterations} 是工具调用轮次的实际上限，
 * 通过 {@link ToolCallRoundGate} 传递给有界资格检查器
 * （BoundedToolExecutionEligibilityChecker），在 ToolCallingAdvisor
 * 的工具调用循环内强制执行。返回结果中的迭代次数为真实值：
 * 1（初始 LLM 调用）+ 实际完成的工具调用轮次。</p>
 *
 * <p><b>异常处理</b>：当 {@code chatWithTools} 抛出平台异常
 * （{@link AgentException} 及其子类，如 {@code LlmCallException}）时，
 * 不向上传播，而是返回 {@link AgentExecutionResult#failure} 结果，
 * 包含错误信息与已记录的执行步骤；非平台异常（编程错误）向上传播。</p>
 *
 * <p>线程安全：{@link ChatService} 与 {@link PrometheusTools} 均线程安全，
 * 本执行器无内部可变状态，组件单例，线程安全。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Component
public class ReActExecutor {

    private static final Logger log = LoggerFactory.getLogger(ReActExecutor.class);

    /**
     * 最大迭代次数默认值。
     *
     * <p>当调用方未显式指定 {@code maxIterations} 时使用此值。
     * 该值作为 ReAct 循环的安全上限记录在执行步骤中，
     * 实际循环由 Spring AI 在单次 LLM 调用内完成。</p>
     */
    public static final int MAX_ITERATIONS = 10;

    /** LLM 对话服务，封装 ChatClient 的工具调用能力。 */
    private final ChatService chatService;

    /** Prometheus 运维指标查询工具，作为 ReAct 模式的 Action 工具集。 */
    private final PrometheusTools prometheusTools;

    /** 工作记忆（可选）：执行过程中写入中间步骤，任务结束清理。 */
    private final ObjectProvider<WorkingMemory> workingMemoryProvider;

    /** 工作记忆条目 key：ReAct 步骤记录。 */
    static final String WORKING_MEMORY_KEY = "react.steps";

    /**
     * 构造 ReActExecutor。
     *
     * <p>由 Spring 容器自动注入依赖。chatService 与 prometheusTools 为必须，
     * 不允许为 null（由 Spring 构造注入保证）；工作记忆为可选组件，
     * smartops.memory.working.enabled=false 时 Provider 内无 Bean，跳过读写。</p>
     *
     * @param chatService           LLM 对话服务，用于发送带工具的对话请求
     * @param prometheusTools       Prometheus 指标查询工具，供 LLM 在 ReAct 循环中调用
     * @param workingMemoryProvider 工作记忆提供者（可选 Bean，ADR-014 工作记忆层）
     */
    public ReActExecutor(ChatService chatService, PrometheusTools prometheusTools,
                         ObjectProvider<WorkingMemory> workingMemoryProvider) {
        this.chatService = chatService;
        this.prometheusTools = prometheusTools;
        this.workingMemoryProvider = workingMemoryProvider;
    }

    /**
     * 执行 ReAct 模式，使用默认最大迭代次数 {@link #MAX_ITERATIONS}。
     *
     * <p>等价于 {@code execute(userInput, conversationId, MAX_ITERATIONS)}。</p>
     *
     * @param userInput      用户自然语言输入，不能为 null 或空白
     * @param conversationId 会话 ID，用于日志追踪与步骤记录
     * @return 执行结果，包含 LLM 最终答案、执行模式、迭代次数与步骤记录
     * @throws IllegalArgumentException 当 {@code userInput} 为 null 或空白时
     */
    public AgentExecutionResult execute(String userInput, String conversationId) {
        return execute(userInput, conversationId, MAX_ITERATIONS);
    }

    /**
     * 执行 ReAct 模式，指定最大迭代次数。
     *
     * <p><b>执行步骤</b>：
     * <ol>
     *   <li>校验用户输入非空非空白，校验 {@code maxIterations} 为正数</li>
     *   <li>记录执行开始步骤（含会话 ID 与最大迭代次数）</li>
     *   <li>调用 {@link ChatService#chatWithTools(String, Object...)} 发送请求，
     *       LLM 内部自动完成 Thought → Action → Observation 循环</li>
     *   <li>成功时记录完成步骤，返回 {@link AgentExecutionResult#success}</li>
     *   <li>异常时记录失败步骤，返回 {@link AgentExecutionResult#failure}</li>
     * </ol></p>
     *
     * <p><b>为什么不手动编排 Thought/Action/Observation 循环</b>：
     * Spring AI 2.0 的 ToolCallingAdvisor 已在 ChatClient 内部实现了
     * 工具调用循环（LLM 决定调用工具 → 执行工具 → 将结果回传 LLM → LLM 继续
     * 推理），无需在应用层手动编排。执行器只需发起一次 {@code chatWithTools}
     * 调用，循环细节由框架处理。由于 2.0 未提供内置迭代上限配置，
     * 本方法通过 {@link ToolCallRoundGate#startRequest(int)} 将
     * {@code maxIterations} 传递给有界资格检查器，在循环内强制执行上限。</p>
     *
     * @param userInput      用户自然语言输入，不能为 null 或空白
     * @param conversationId 会话 ID，用于日志追踪与步骤记录
     * @param maxIterations  最大迭代次数，必须为正数
     * @return 执行结果，成功时包含答案与步骤，失败时包含错误信息与已记录步骤
     * @throws IllegalArgumentException 当 {@code userInput} 为 null/空白，
     *                                  或 {@code maxIterations} 非正数时
     */
    public AgentExecutionResult execute(String userInput, String conversationId, int maxIterations) {
        // 输入校验：userInput 不能为 null 或空白
        if (userInput == null || userInput.isBlank()) {
            throw new IllegalArgumentException("用户输入不能为 null 或空白");
        }
        // 迭代次数校验：maxIterations 必须为正数，作为安全上限
        if (maxIterations <= 0) {
            throw new IllegalArgumentException("最大迭代次数必须为正数: " + maxIterations);
        }

        log.info("ReAct 执行开始: conversationId={}, maxIterations={}", conversationId, maxIterations);

        // 记录执行步骤，便于追踪与调试
        List<String> steps = new ArrayList<>();
        steps.add(String.format("ReAct 执行开始，会话ID: %s, 最大迭代次数: %d", conversationId, maxIterations));

        try {
            // 声明本次请求的工具调用轮次上限，由有界资格检查器在工具循环内强制执行
            ToolCallRoundGate.startRequest(maxIterations);

            // 通过 ChatService 发送带工具的对话请求（会话级，记忆按 conversationId 隔离），
            // LLM 内部完成 ReAct 循环
            String answer = chatService.chatWithTools(conversationId, userInput, prometheusTools);

            // 真实迭代次数 = 1 次初始 LLM 调用 + 实际完成的工具调用轮次
            int actualIterations = 1 + ToolCallRoundGate.lastCompletedRounds();
            steps.add(String.format("ReAct 执行完成，用户输入: %s，实际迭代轮次: %d", userInput, actualIterations));
            saveWorkingMemory(conversationId, steps);
            log.info("ReAct 执行完成: conversationId={}, iterations={}", conversationId, actualIterations);

            return AgentExecutionResult.success(answer, AgentMode.REACT, actualIterations, steps);
        } catch (AgentException e) {
            // 平台异常（LLM 调用失败、工具执行失败等，经 ChatService 异常翻译）转为失败结果；
            // 非平台异常属编程错误，向上传播暴露问题
            log.error("ReAct 执行失败: conversationId={}, error={}", conversationId, e.getMessage(), e);
            steps.add(String.format("ReAct 执行失败，错误: %s", e.getMessage()));
            saveWorkingMemory(conversationId, steps);

            // 失败时迭代次数记为 0（未完成任何一次成功执行）
            return AgentExecutionResult.failure(AgentMode.REACT, 0, steps, e.getMessage());
        } finally {
            // 必须清理线程级状态，防止 Tomcat 线程复用导致泄露到无关请求
            ToolCallRoundGate.clearRequest();
            // 工作记忆生命周期为单次任务，结束即清理（ADR-014）
            clearWorkingMemory(conversationId);
        }
    }

    /**
     * 将当前执行步骤写入工作记忆；会话 ID 为 null 或工作记忆未启用时跳过。
     *
     * @param conversationId 会话 ID
     * @param steps          执行步骤记录
     */
    private void saveWorkingMemory(String conversationId, List<String> steps) {
        if (conversationId == null) {
            return;
        }
        WorkingMemory workingMemory = workingMemoryProvider.getIfAvailable();
        if (workingMemory != null) {
            workingMemory.put(conversationId, WORKING_MEMORY_KEY, String.join("\n", steps));
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
