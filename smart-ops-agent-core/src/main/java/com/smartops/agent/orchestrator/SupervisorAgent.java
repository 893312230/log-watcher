package com.smartops.agent.orchestrator;

import com.smartops.agent.a2a.AgentCardRegistry;
import com.smartops.agent.security.ConfirmationContext;
import com.smartops.common.enums.AgentRole;
import com.smartops.common.enums.TaskStatus;
import com.smartops.common.exception.SecurityViolationException;
import com.smartops.common.model.A2aResponse;
import com.smartops.common.model.AgentExecutionResult;
import com.smartops.common.model.SubTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/**
 * Supervisor Agent（主管 Agent）。
 *
 * <p>对应 agent.md 阶段三特性5。Multi-Agent 架构的核心编排组件，
 * 负责任务分解、Worker 分配、结果聚合。</p>
 *
 * <p><b>编排流程</b>：
 * <ol>
 *   <li>接收复杂运维任务（多步骤、跨领域）</li>
 *   <li>根据任务描述分解为多个子任务，每个子任务分配给合适的 Worker 角色</li>
 *   <li>按 {@link SubTask#priority()} 升序通过 {@link TaskDispatcher} 顺序分发，
 *       每个子任务带超时保护（{@code smartops.supervisor.subtask-timeout-seconds}）</li>
 *   <li>收集所有子任务的执行结果</li>
 *   <li>聚合结果，生成最终综合回复</li>
 * </ol></p>
 *
 * <p><b>成功语义</b>：任一子任务成功即整体 success=true；
 * 全部子任务失败时返回 success=false 且 errorMessage 携带失败清单，
 * 避免"全军覆没仍报成功"的假阳性。</p>
 *
 * <p><b>异常处理</b>：
 * <ul>
 *   <li>单个子任务失败/超时不影响其他子任务执行</li>
 *   <li>安全违规异常（{@link SecurityViolationException}）穿透向上传播，
 *       由 API 层发起人工确认流程</li>
 *   <li>任务分解失败（无匹配关键词）时返回单步骤结果</li>
 * </ul></p>
 *
 * <p>线程安全：依赖组件均线程安全，本组件无内部可变状态。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Component
public class SupervisorAgent {

    private static final Logger log = LoggerFactory.getLogger(SupervisorAgent.class);

    /** 默认子任务超时（秒）。 */
    static final long DEFAULT_SUBTASK_TIMEOUT_SECONDS = 60L;

    /** 任务分发器。 */
    private final TaskDispatcher dispatcher;

    /** Agent Card 注册中心。 */
    private final AgentCardRegistry registry;

    /** 子任务超时时间（秒）。 */
    private final long subtaskTimeoutSeconds;

    /**
     * 构造 Supervisor Agent（使用默认子任务超时 60 秒）。
     *
     * @param dispatcher 任务分发器
     * @param registry   Agent Card 注册中心
     */
    public SupervisorAgent(TaskDispatcher dispatcher, AgentCardRegistry registry) {
        this(dispatcher, registry, DEFAULT_SUBTASK_TIMEOUT_SECONDS);
    }

    /**
     * 构造 Supervisor Agent（Spring 注入，子任务超时可配置）。
     *
     * @param dispatcher            任务分发器
     * @param registry              Agent Card 注册中心
     * @param subtaskTimeoutSeconds 子任务超时时间（秒），必须为正数
     */
    @Autowired
    public SupervisorAgent(TaskDispatcher dispatcher, AgentCardRegistry registry,
                           @Value("${smartops.supervisor.subtask-timeout-seconds:60}")
                           long subtaskTimeoutSeconds) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher 不能为 null");
        this.registry = Objects.requireNonNull(registry, "registry 不能为 null");
        if (subtaskTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("子任务超时时间必须为正数");
        }
        this.subtaskTimeoutSeconds = subtaskTimeoutSeconds;
    }

    /**
     * 编排执行复杂运维任务。
     *
     * <p>完整流程：任务分解 → 按优先级排序 → 带超时顺序分发 → 结果聚合。</p>
     *
     * @param userInput      用户输入的复杂运维任务
     * @param conversationId 会话 ID
     * @return 执行结果，包含聚合后的综合回复
     * @throws IllegalArgumentException 当 userInput 为 null 或空白时
     */
    public AgentExecutionResult orchestrate(String userInput, String conversationId) {
        if (userInput == null || userInput.isBlank()) {
            throw new IllegalArgumentException("用户输入不能为 null 或空白");
        }

        String parentTaskId = conversationId != null ? conversationId : UUID.randomUUID().toString();
        log.info("Supervisor 编排开始: parentTaskId={}, input={}", parentTaskId, userInput);

        // 1. 任务分解并按优先级升序排序
        List<SubTask> subTasks = decompose(userInput, parentTaskId);
        if (subTasks.isEmpty()) {
            log.warn("任务分解未生成子任务，返回默认结果");
            return AgentExecutionResult.success(
                    "无法分解该任务，请提供更具体的运维指令",
                    com.smartops.common.enums.AgentMode.PLAN_AND_SOLVE,
                    0, List.of());
        }
        subTasks.sort(Comparator.comparingInt(SubTask::priority));

        log.info("任务分解完成: subTaskCount={}", subTasks.size());

        // 2. 按优先级顺序分发子任务并收集结果（每个子任务带超时保护）
        List<String> executionSteps = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;
        List<String> results = new ArrayList<>();

        for (SubTask subTask : subTasks) {
            log.info("执行子任务: taskId={}, role={}, instruction={}",
                    subTask.taskId(), subTask.targetRole(), subTask.instruction());

            executionSteps.add(String.format("子任务[%s]: %s → %s",
                    subTask.targetRole().getDisplayName(),
                    subTask.instruction(),
                    TaskStatus.RUNNING.name()));

            A2aResponse response = dispatchWithTimeout(subTask);

            if (response.isSuccess()) {
                successCount++;
                results.add(response.result());
                executionSteps.set(executionSteps.size() - 1,
                        executionSteps.get(executionSteps.size() - 1).replace(
                                TaskStatus.RUNNING.name(), TaskStatus.SUCCESS.name()));
            } else {
                failureCount++;
                results.add("失败: " + response.error());
                executionSteps.set(executionSteps.size() - 1,
                        executionSteps.get(executionSteps.size() - 1).replace(
                                TaskStatus.RUNNING.name(), TaskStatus.FAILED.name()));
            }
        }

        // 3. 聚合结果：任一子任务成功即整体成功；全部失败返回失败清单
        String aggregatedResult = aggregateResults(userInput, results, successCount, failureCount);

        log.info("Supervisor 编排完成: parentTaskId={}, success={}, failure={}",
                parentTaskId, successCount, failureCount);

        if (successCount == 0) {
            return AgentExecutionResult.failure(
                    com.smartops.common.enums.AgentMode.PLAN_AND_SOLVE,
                    subTasks.size(),
                    executionSteps,
                    aggregatedResult);
        }
        return AgentExecutionResult.success(
                aggregatedResult,
                com.smartops.common.enums.AgentMode.PLAN_AND_SOLVE,
                subTasks.size(),
                executionSteps);
    }

    /**
     * 带超时保护地分发单个子任务。
     *
     * <p>超时或执行异常转换为失败响应（不中断后续子任务）；
     * {@link SecurityViolationException} 拆封后向上传播。</p>
     *
     * <p>人工确认标记（{@link ConfirmationContext}，ThreadLocal）在调用线程捕获后
     * 透传到异步执行线程，保证异步分发不改变安全门语义。</p>
     *
     * @param subTask 子任务
     * @return A2A 响应（超时/异常时为失败响应）
     */
    private A2aResponse dispatchWithTimeout(SubTask subTask) {
        boolean confirmed = ConfirmationContext.isConfirmed();
        try {
            return CompletableFuture
                    .supplyAsync(() -> {
                        if (confirmed) {
                            ConfirmationContext.markConfirmed();
                        }
                        try {
                            return dispatcher.dispatch(subTask);
                        } finally {
                            if (confirmed) {
                                ConfirmationContext.clear();
                            }
                        }
                    })
                    .orTimeout(subtaskTimeoutSeconds, TimeUnit.SECONDS)
                    .join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof SecurityViolationException securityViolation) {
                throw securityViolation;
            }
            log.warn("子任务执行超时或异常: taskId={}, timeoutSeconds={}, cause={}",
                    subTask.taskId(), subtaskTimeoutSeconds, e.getMessage());
            return A2aResponse.failure(
                    UUID.randomUUID().toString(),
                    subTask.taskId(),
                    subTask.targetRole(),
                    "子任务执行超时或异常（超时上限 " + subtaskTimeoutSeconds + "s）");
        }
    }

    /**
     * 任务分解。
     *
     * <p>基于关键词规则将复杂任务分解为子任务。后续可升级为 LLM 分解。</p>
     *
     * @param userInput    用户输入
     * @param parentTaskId 父任务 ID
     * @return 子任务列表
     */
    List<SubTask> decompose(String userInput, String parentTaskId) {
        List<SubTask> tasks = new ArrayList<>();
        String normalizedInput = userInput.toLowerCase();

        // 监控相关
        if (containsAny(normalizedInput, "监控", "指标", "告警", "metric", "alert", "cpu", "内存", "磁盘")) {
            tasks.add(SubTask.create(
                    UUID.randomUUID().toString(),
                    parentTaskId,
                    AgentRole.MONITOR,
                    "查询并分析相关监控指标: " + userInput,
                    1));
        }

        // 分析相关
        if (containsAny(normalizedInput, "分析", "根因", "日志", "排查", "原因", "为什么", "analyze", "root")) {
            tasks.add(SubTask.create(
                    UUID.randomUUID().toString(),
                    parentTaskId,
                    AgentRole.ANALYZE,
                    "进行根因分析: " + userInput,
                    2));
        }

        // 执行相关
        if (containsAny(normalizedInput, "重启", "扩缩容", "执行", "部署", "配置变更", "restart", "scale", "deploy")) {
            tasks.add(SubTask.create(
                    UUID.randomUUID().toString(),
                    parentTaskId,
                    AgentRole.EXECUTE,
                    "执行运维操作: " + userInput,
                    3));
        }

        // 知识相关
        if (containsAny(normalizedInput, "知识", "文档", "最佳实践", "怎么", "如何", "knowledge", "document")) {
            tasks.add(SubTask.create(
                    UUID.randomUUID().toString(),
                    parentTaskId,
                    AgentRole.KNOWLEDGE,
                    "查询运维知识库: " + userInput,
                    4));
        }

        return tasks;
    }

    /**
     * 聚合子任务结果，生成综合回复。
     *
     * @param originalInput 原始用户输入
     * @param results       子任务结果列表
     * @param successCount  成功数
     * @param failureCount  失败数
     * @return 聚合后的综合回复
     */
    private String aggregateResults(String originalInput, List<String> results,
                                    int successCount, int failureCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("【Multi-Agent 协作结果】\n");
        sb.append(String.format("任务: %s\n", originalInput));
        sb.append(String.format("执行情况: 成功 %d 个, 失败 %d 个\n\n", successCount, failureCount));
        sb.append("各 Agent 执行结果:\n");
        for (int i = 0; i < results.size(); i++) {
            sb.append(String.format("  %d. %s\n", i + 1, results.get(i)));
        }
        if (failureCount == 0) {
            sb.append("\n所有子任务均已成功完成。");
        } else if (successCount > 0) {
            sb.append("\n部分子任务执行失败，请检查失败原因。");
        } else {
            sb.append("\n所有子任务均执行失败，请稍后重试或联系管理员。");
        }
        return sb.toString();
    }

    /**
     * 判断输入是否包含任一关键词。
     *
     * @param input    输入文本（已小写化）
     * @param keywords 关键词数组
     * @return 如果包含任一关键词返回 true
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
}
