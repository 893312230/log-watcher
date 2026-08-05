package com.smartops.agent.router;

import com.smartops.common.enums.AgentMode;
import com.smartops.common.enums.IntentType;
import com.smartops.common.model.IntentResult;
import com.smartops.common.model.TaskComplexity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 任务复杂度分析器。
 *
 * <p>对应 agent.md 阶段二任务6。根据意图识别结果和用户输入文本特征，
 * 预估任务步骤数、依赖关系、实时性要求和探索性，为路由决策引擎提供
 * ReAct / Plan-and-Solve 模式选择依据。</p>
 *
 * <p><b>分析维度</b>：
 * <ul>
 *   <li>步骤数预估：通过文本中的步骤连接词（然后、接着、之后等）和
 *       动作动词数量推断</li>
 *   <li>依赖关系：多步骤任务且步骤间有顺序依赖时判定为有依赖</li>
 *   <li>实时性：告警分析、实时监控类任务需要即时反馈</li>
 *   <li>探索性：根因分析、故障排查类任务需要迭代探索</li>
 * </ul></p>
 *
 * <p><b>路由建议</b>：
 * <ul>
 *   <li>QUERY_METRIC / KNOWLEDGE_QA → 简单任务，REACT</li>
 *   <li>ANALYZE_ALERT / ROOT_CAUSE → 实时/探索性任务，REACT</li>
 *   <li>TREND_ANALYSIS → 中等任务，REACT</li>
 *   <li>EXECUTE_OPERATION（多步骤）→ 结构化任务，PLAN_AND_SOLVE</li>
 * </ul></p>
 *
 * <p>线程安全：无状态，组件单例，线程安全。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Component
public class TaskAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(TaskAnalyzer.class);

    /** 步骤连接词正则：匹配"然后、接着、之后、再、最后"等步骤衔接词。 */
    private static final Pattern STEP_CONNECTOR_PATTERN = Pattern.compile(
            "(然后|接着|之后|再|最后|第一步|第二步|第三步|首先|其次)"
    );

    /** 多动作动词正则：匹配连续出现的操作动作词。 */
    private static final Pattern MULTI_ACTION_PATTERN = Pattern.compile(
            "(重启|扩容|缩容|修改|部署|回滚|清理|停止|启动|备份|恢复|迁移)"
    );

    /** 实时性关键词：匹配需要即时响应的场景。 */
    private static final Pattern REALTIME_PATTERN = Pattern.compile(
            "(实时|当前|现在|立刻|马上|紧急|告警|报警)"
    );

    /**
     * 探索性关键词：匹配需要迭代分析的场景。
     * 不包含"分析"——该词出现在几乎所有运维请求中（告警分析、趋势分析等），
     * 会使探索性判定失去区分度，导致 PLAN_AND_SOLVE 不可达。
     */
    private static final Pattern EXPLORATORY_PATTERN = Pattern.compile(
            "(为什么|根因|原因|排查|诊断|定位)"
    );

    /** 最大步骤数上限。 */
    private static final int MAX_STEPS = 10;

    /**
     * 分析任务复杂度。
     *
     * <p>根据意图类型和文本特征推断步骤数、依赖关系、实时性、探索性，
     * 并给出建议的执行模式。</p>
     *
     * @param userInput  用户输入文本，不能为 null 或空白
     * @param intentResult 意图识别结果，不能为 null
     * @return 任务复杂度分析结果
     * @throws IllegalArgumentException 当输入为 null/空白或意图结果为 null 时
     */
    public TaskComplexity analyze(String userInput, IntentResult intentResult) {
        if (userInput == null || userInput.isBlank()) {
            throw new IllegalArgumentException("用户输入不能为 null 或空白");
        }
        if (intentResult == null) {
            throw new IllegalArgumentException("意图识别结果不能为 null");
        }

        log.debug("开始分析任务复杂度: intent={}", intentResult.intentType());

        IntentType intent = intentResult.intentType();
        boolean realTimeRequired = REALTIME_PATTERN.matcher(userInput).find();
        // EXECUTE_OPERATION 意图永不判探索性：操作类任务即使含"排查"等词，
        // 也按结构化步骤处理，避免被打回 ReAct 导致 PLAN_AND_SOLVE 不可达
        boolean isExploratory = EXPLORATORY_PATTERN.matcher(userInput).find()
                && intent != IntentType.EXECUTE_OPERATION;

        // 根据意图类型和文本特征推断步骤数
        int stepCount = estimateSteps(userInput, intent);
        boolean hasDependencies = stepCount >= 2 && detectDependencies(userInput);

        // 生成步骤摘要
        List<String> steps = generateStepSummaries(intent, stepCount);

        // 根据复杂度选择执行模式
        AgentMode suggestedMode = selectMode(intent, stepCount, hasDependencies,
                realTimeRequired, isExploratory);

        TaskComplexity complexity = new TaskComplexity(
                Math.min(stepCount, MAX_STEPS),
                hasDependencies,
                realTimeRequired,
                isExploratory,
                suggestedMode,
                steps
        );

        log.info("任务复杂度分析完成: steps={}, deps={}, realtime={}, exploratory={}, mode={}",
                complexity.estimatedSteps(), complexity.hasDependencies(),
                complexity.realTimeRequired(), complexity.isExploratory(),
                complexity.suggestedMode());

        return complexity;
    }

    /**
     * 根据意图类型和文本特征预估步骤数。
     *
     * <p>预估策略：
     * <ul>
     *   <li>QUERY_METRIC / KNOWLEDGE_QA：默认 1 步</li>
     *   <li>TREND_ANALYSIS：默认 2 步（查询 + 分析趋势）</li>
     *   <li>ANALYZE_ALERT：默认 2 步（查询告警 + 分析）</li>
     *   <li>ROOT_CAUSE：默认 3 步（探索性，需多轮分析）</li>
     *   <li>EXECUTE_OPERATION：根据动作动词数量预估</li>
     *   <li>UNKNOWN：默认 1 步</li>
     * </ul></p>
     *
     * @param userInput 用户输入
     * @param intent    意图类型
     * @return 预估步骤数（1-10）
     */
    private int estimateSteps(String userInput, IntentType intent) {
        // 先检查文本中是否有显式的步骤连接词
        var connectorMatcher = STEP_CONNECTOR_PATTERN.matcher(userInput);
        int connectorCount = 0;
        while (connectorMatcher.find()) {
            connectorCount++;
        }

        // 基础步骤数由意图类型决定
        int baseSteps = switch (intent) {
            case QUERY_METRIC, KNOWLEDGE_QA, UNKNOWN -> 1;
            case TREND_ANALYSIS -> 2;
            case ANALYZE_ALERT -> 2;
            case ROOT_CAUSE -> 3;
            case EXECUTE_OPERATION -> countActions(userInput);
        };

        // 有步骤连接词时，取基础步骤与连接词推断步数的较大者，
        // 避免"基础步骤 + 连接词数"对同一批步骤重复计数
        int totalSteps = Math.max(baseSteps, connectorCount + 1);

        return Math.max(1, Math.min(totalSteps, MAX_STEPS));
    }

    /**
     * 统计文本中的操作动作数量。
     *
     * @param userInput 用户输入
     * @return 动作数量（至少 1）
     */
    private int countActions(String userInput) {
        var matcher = MULTI_ACTION_PATTERN.matcher(userInput);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return Math.max(count, 1);
    }

    /**
     * 检测步骤间是否存在依赖关系。
     *
     * <p>当文本包含步骤连接词（然后、接着等）且涉及多个操作时，判定为有依赖。</p>
     *
     * @param userInput 用户输入
     * @return true 如果存在依赖关系
     */
    private boolean detectDependencies(String userInput) {
        return STEP_CONNECTOR_PATTERN.matcher(userInput).find()
                || MULTI_ACTION_PATTERN.matcher(userInput).results().count() >= 2;
    }

    /**
     * 根据复杂度维度选择执行模式。
     *
     * <p>选择策略：
     * <ul>
     *   <li>探索性任务（根因/排查类）→ REACT（迭代反馈优先于全局规划）；
     *       EXECUTE_OPERATION 意图例外：操作类任务即使含"排查"等词也按
     *       结构化步骤处理，永不判探索性</li>
     *   <li>实时性要求 → REACT（作为决胜条件：实时任务即便多步骤，
     *       也需要即时反馈而非事前全局规划）</li>
     *   <li>步骤数 ≥ 3 或（≥ 2 且有依赖）→ PLAN_AND_SOLVE</li>
     *   <li>默认 → REACT（简单任务用 ReAct 更灵活）</li>
     * </ul></p>
     *
     * @param intent           意图类型
     * @param stepCount        步骤数
     * @param hasDependencies  是否有依赖
     * @param realTimeRequired 是否需要实时性
     * @param isExploratory    是否探索性
     * @return 建议的执行模式
     */
    private AgentMode selectMode(IntentType intent, int stepCount, boolean hasDependencies,
                                  boolean realTimeRequired, boolean isExploratory) {
        // 探索性任务（根因分析）→ ReAct（探索需迭代反馈）
        if (isExploratory || intent == IntentType.ROOT_CAUSE) {
            return AgentMode.REACT;
        }

        // 实时性作为决胜条件：实时任务优先迭代反馈而非全局规划
        if (realTimeRequired || intent == IntentType.ANALYZE_ALERT) {
            return AgentMode.REACT;
        }

        // 多步骤 + 依赖 → Plan-and-Solve（非探索性、非实时的结构化任务）
        if (stepCount >= 3 || (stepCount >= 2 && hasDependencies)) {
            return AgentMode.PLAN_AND_SOLVE;
        }

        // 默认用 ReAct（简单任务更灵活）
        return AgentMode.REACT;
    }

    /**
     * 生成步骤摘要列表（用于展示和日志）。
     *
     * @param intent    意图类型
     * @param stepCount 步骤数
     * @return 步骤摘要列表
     */
    private List<String> generateStepSummaries(IntentType intent, int stepCount) {
        List<String> steps = new ArrayList<>(stepCount);

        for (int i = 0; i < stepCount; i++) {
            String step = switch (intent) {
                case QUERY_METRIC -> i == 0 ? "查询指标数据" : "返回查询结果";
                case TREND_ANALYSIS -> i == 0 ? "查询历史数据" : i == 1 ? "分析趋势变化" : "生成趋势报告";
                case ANALYZE_ALERT -> i == 0 ? "获取告警详情" : i == 1 ? "分析告警原因" : "提供处理建议";
                case ROOT_CAUSE -> i == 0 ? "收集系统状态" : i == 1 ? "分析异常指标" : i == 2 ? "定位根因" : "提供修复建议";
                case EXECUTE_OPERATION -> i == 0 ? "执行运维操作" : "验证操作结果";
                case KNOWLEDGE_QA -> i == 0 ? "检索知识库" : "返回答案";
                case UNKNOWN -> i == 0 ? "处理用户请求" : "返回结果";
            };
            steps.add(step);
        }

        return steps;
    }
}
