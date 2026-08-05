package com.smartops.agent.router;

import com.smartops.agent.intent.IntentPipeline;
import com.smartops.agent.orchestrator.SupervisorAgent;
import com.smartops.agent.plan.PlanAndSolveExecutor;
import com.smartops.agent.react.ReActExecutor;
import com.smartops.common.enums.AgentMode;
import com.smartops.common.exception.AgentException;
import com.smartops.common.exception.SecurityViolationException;
import com.smartops.common.model.AgentExecutionResult;
import com.smartops.common.model.IntentResult;
import com.smartops.common.model.TaskComplexity;
import com.smartops.infrastructure.observability.Observability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 路由决策引擎。
 *
 * <p>对应 agent.md 阶段二特性8 + 阶段三集成。作为 Agent 平台的核心编排组件，
 * 根据意图识别结果和任务复杂度分析，动态选择执行模式：
 * <ul>
 *   <li>单领域简单任务 → ReAct 或 Plan-and-Solve</li>
 *   <li>跨领域复杂任务 → SupervisorAgent 多 Agent 协作</li>
 * </ul></p>
 *
 * <p><b>路由流程</b>：
 * <ol>
 *   <li>调用 {@link IntentPipeline#recognize(String)} 获取 {@link IntentResult}</li>
 *   <li>调用 {@link TaskAnalyzer#analyze(String, IntentResult)} 获取 {@link TaskComplexity}</li>
 *   <li>判断是否为跨领域任务（匹配 2+ Worker 域关键词）</li>
 *   <li>选择执行器：
 *     <ul>
 *       <li>跨领域复杂任务 → {@link SupervisorAgent#orchestrate}</li>
 *       <li>{@link AgentMode#REACT} → {@link ReActExecutor#execute}</li>
 *       <li>{@link AgentMode#PLAN_AND_SOLVE} → {@link PlanAndSolveExecutor#execute}</li>
 *     </ul>
 *   </li>
 *   <li>记录路由决策日志</li>
 *   <li>返回执行结果</li>
 * </ol></p>
 *
 * <p><b>异常处理</b>：
 * <ul>
 *   <li>意图识别抛出平台异常（{@link AgentException} 及其子类）时，返回失败结果（mode=REACT）</li>
 *   <li>执行器抛出平台异常时，返回失败结果（mode=已选模式）；
 *       非平台异常（编程错误）向上传播</li>
 *   <li>输入为 null 或空白时，抛出 {@link IllegalArgumentException}</li>
 * </ul></p>
 *
 * <p>线程安全：依赖组件均线程安全，本组件无内部可变状态，组件单例，线程安全。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Component
public class AgentRouter {

    private static final Logger log = LoggerFactory.getLogger(AgentRouter.class);

    /** 意图识别 Pipeline。 */
    private final IntentPipeline intentPipeline;

    /** 任务复杂度分析器。 */
    private final TaskAnalyzer taskAnalyzer;

    /** ReAct 执行器。 */
    private final ReActExecutor reactExecutor;

    /** Plan-and-Solve 执行器。 */
    private final PlanAndSolveExecutor planAndSolveExecutor;

    /** Supervisor Agent（多 Agent 协作编排）。 */
    private final SupervisorAgent supervisorAgent;

    /** 可观测性门面（任务执行指标+审计），可为 null（测试或裁剪场景）。 */
    private final Observability observability;

    /**
     * 构造路由决策引擎（无可观测性，供测试使用）。
     *
     * @param intentPipeline       意图识别 Pipeline
     * @param taskAnalyzer         任务复杂度分析器
     * @param reactExecutor        ReAct 执行器
     * @param planAndSolveExecutor Plan-and-Solve 执行器
     * @param supervisorAgent      Supervisor Agent
     */
    public AgentRouter(
            IntentPipeline intentPipeline,
            TaskAnalyzer taskAnalyzer,
            ReActExecutor reactExecutor,
            PlanAndSolveExecutor planAndSolveExecutor,
            SupervisorAgent supervisorAgent) {
        this(intentPipeline, taskAnalyzer, reactExecutor, planAndSolveExecutor,
                supervisorAgent, null);
    }

    /**
     * 构造路由决策引擎。
     *
     * <p>类内存在多个构造器，须以 {@link Autowired} 显式标注注入入口。</p>
     *
     * @param intentPipeline       意图识别 Pipeline
     * @param taskAnalyzer         任务复杂度分析器
     * @param reactExecutor        ReAct 执行器
     * @param planAndSolveExecutor Plan-and-Solve 执行器
     * @param supervisorAgent      Supervisor Agent
     * @param observability        可观测性门面（任务执行指标+审计），可为 null
     */
    @Autowired
    public AgentRouter(
            IntentPipeline intentPipeline,
            TaskAnalyzer taskAnalyzer,
            ReActExecutor reactExecutor,
            PlanAndSolveExecutor planAndSolveExecutor,
            SupervisorAgent supervisorAgent,
            Observability observability) {
        this.intentPipeline = intentPipeline;
        this.taskAnalyzer = taskAnalyzer;
        this.reactExecutor = reactExecutor;
        this.planAndSolveExecutor = planAndSolveExecutor;
        this.supervisorAgent = supervisorAgent;
        this.observability = observability;
    }

    /**
     * 路由决策与执行。
     *
     * @param userInput      用户输入文本，不能为 null 或空白
     * @param conversationId 会话 ID，用于日志追踪（可为 null）
     * @return 执行结果
     * @throws IllegalArgumentException 当 userInput 为 null 或空白时
     */
    public AgentExecutionResult route(String userInput, String conversationId) {
        if (userInput == null || userInput.isBlank()) {
            throw new IllegalArgumentException("用户输入不能为 null 或空白");
        }

        // 会话 ID 归一化：为空时生成新 UUID。
        // 下游执行器要求非空会话 ID 以隔离短期记忆，杜绝跨用户上下文泄露
        String normalizedConversationId = conversationId != null && !conversationId.isBlank()
                ? conversationId
                : UUID.randomUUID().toString();

        log.info("路由决策开始: conversationId={}", normalizedConversationId);
        long startNanos = System.nanoTime();

        // 1. 意图识别
        IntentResult intentResult;
        try {
            intentResult = intentPipeline.recognize(userInput);
        } catch (AgentException e) {
            log.error("意图识别失败: conversationId={}, error={}", normalizedConversationId, e.getMessage(), e);
            observeTaskExecution(AgentMode.REACT.name(), normalizedConversationId, false,
                    startNanos, "意图识别失败: " + e.getMessage());
            return AgentExecutionResult.failure(AgentMode.REACT, 0, List.of(),
                    "意图识别失败: " + e.getMessage());
        }

        // 2. 任务复杂度分析
        TaskComplexity complexity = taskAnalyzer.analyze(userInput, intentResult);
        AgentMode selectedMode = complexity.suggestedMode();

        // 3. 判断是否为跨领域复杂任务（需多 Agent 协作）
        boolean useSupervisor = shouldUseSupervisor(userInput, complexity);

        // 4. 记录路由决策日志
        log.info("路由决策: intent={}, confidence={}, steps={}, deps={}, realtime={}, exploratory={}, selectedMode={}, useSupervisor={}",
                intentResult.intentType(), intentResult.confidence(),
                complexity.estimatedSteps(), complexity.hasDependencies(),
                complexity.realTimeRequired(), complexity.isExploratory(),
                selectedMode, useSupervisor);

        // 5. 选择执行器并执行
        String executedMode = useSupervisor ? "SUPERVISOR" : selectedMode.name();
        try {
            AgentExecutionResult result;
            if (useSupervisor) {
                log.info("使用 SupervisorAgent 多 Agent 协作执行");
                result = supervisorAgent.orchestrate(userInput, normalizedConversationId);
            } else if (selectedMode == AgentMode.REACT) {
                result = reactExecutor.execute(userInput, normalizedConversationId);
            } else {
                result = planAndSolveExecutor.execute(userInput, normalizedConversationId);
            }
            observeTaskExecution(executedMode, normalizedConversationId, result.success(),
                    startNanos, result.success()
                            ? "迭代次数: " + result.iterations()
                            : result.errorMessage());
            return result;
        } catch (SecurityViolationException e) {
            // 安全违规（高危操作未确认）向上传播，由 API 层发起人工确认流程
            observeTaskExecution(executedMode, normalizedConversationId, false,
                    startNanos, "安全确认待处理: " + e.getMessage());
            throw e;
        } catch (AgentException e) {
            // 平台异常（如 LLM 调用失败）转为失败结果；
            // 非平台异常属编程错误，向上传播暴露问题
            log.error("执行器执行失败: conversationId={}, mode={}, useSupervisor={}, error={}",
                    normalizedConversationId, selectedMode, useSupervisor, e.getMessage(), e);
            observeTaskExecution(executedMode, normalizedConversationId, false,
                    startNanos, "执行器执行失败: " + e.getMessage());
            return AgentExecutionResult.failure(selectedMode, 0, List.of(),
                    "执行器执行失败: " + e.getMessage());
        }
    }

    /**
     * 任务执行观测（指标+审计）：observability 缺失时静默跳过。
     */
    private void observeTaskExecution(String mode, String conversationId, boolean success,
                                      long startNanos, String detail) {
        if (observability != null) {
            observability.recordTaskExecution(mode, conversationId, success,
                    (System.nanoTime() - startNanos) / 1_000_000, detail);
        }
    }

    /**
     * 判断是否应使用 Supervisor 多 Agent 协作。
     *
     * <p>判断条件：任务跨 2+ 个 Worker 领域（监控/分析/执行/知识），
     * 且预估步骤数 >= 3。跨领域任务需要多个专业 Agent 协作完成。</p>
     *
     * @param userInput   用户输入
     * @param complexity  任务复杂度
     * @return 如果应使用 Supervisor 返回 true
     */
    boolean shouldUseSupervisor(String userInput, TaskComplexity complexity) {
        if (complexity.estimatedSteps() < 3) {
            return false;
        }
        return countDomainMatches(userInput.toLowerCase()) >= 2;
    }

    /**
     * 统计用户输入匹配的 Worker 领域数量。
     *
     * @param lowerInput 已小写化的用户输入
     * @return 匹配的领域数量（0-4）
     */
    private int countDomainMatches(String lowerInput) {
        int count = 0;
        if (containsAny(lowerInput, "监控", "指标", "告警", "metric", "alert", "cpu", "内存", "磁盘")) {
            count++;
        }
        if (containsAny(lowerInput, "分析", "根因", "日志", "排查", "原因", "为什么", "analyze", "root")) {
            count++;
        }
        if (containsAny(lowerInput, "重启", "扩缩容", "执行", "部署", "配置变更", "restart", "scale", "deploy")) {
            count++;
        }
        if (containsAny(lowerInput, "知识", "文档", "最佳实践", "怎么", "如何", "knowledge", "document")) {
            count++;
        }
        return count;
    }

    /**
     * 判断输入是否包含任一关键词。
     */
    private boolean containsAny(String input, String... keywords) {
        if (keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && input.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取路由决策信息（不执行）。
     *
     * @param userInput 用户输入文本
     * @return 路由决策信息
     * @throws IllegalArgumentException 当 userInput 为 null 或空白时
     */
    public RoutingDecision getRoutingDecision(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            throw new IllegalArgumentException("用户输入不能为 null 或空白");
        }

        IntentResult intentResult = intentPipeline.recognize(userInput);
        TaskComplexity complexity = taskAnalyzer.analyze(userInput, intentResult);
        AgentMode selectedMode = complexity.suggestedMode();
        boolean useSupervisor = shouldUseSupervisor(userInput, complexity);

        log.info("路由决策（不执行）: intent={}, selectedMode={}, useSupervisor={}",
                intentResult.intentType(), selectedMode, useSupervisor);

        return new RoutingDecision(intentResult, complexity, selectedMode, useSupervisor);
    }

    /**
     * 路由决策信息。
     *
     * @param intentResult   意图识别结果
     * @param complexity     任务复杂度分析结果
     * @param selectedMode   选择的执行模式
     * @param useSupervisor  是否使用 Supervisor 多 Agent 协作
     */
    public record RoutingDecision(
            IntentResult intentResult,
            TaskComplexity complexity,
            AgentMode selectedMode,
            boolean useSupervisor
    ) {}
}
