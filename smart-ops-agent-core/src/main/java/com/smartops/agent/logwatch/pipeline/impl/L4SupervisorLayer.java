package com.smartops.agent.logwatch.pipeline.impl;

import com.smartops.agent.logwatch.pipeline.AnalysisLayer;
import com.smartops.agent.orchestrator.SupervisorAgent;
import com.smartops.common.model.AgentExecutionResult;
import com.smartops.domain.logwatch.AnalysisContext;
import com.smartops.domain.logwatch.AnalysisOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDate;

/**
 * L5 Supervisor 多 Agent 会诊层（最高层，条件触发）。
 *
 * <p>仅当 L4 标记 {@link AnalysisContext#isEscalate()} 时工作：
 * 将告警摘要与 L4 初步结论提交 {@link SupervisorAgent#orchestrate}，
 * 由多 Agent（监控/分析/执行/知识库）联合会诊复杂故障。
 * 护栏：</p>
 * <ul>
 *   <li>日调用上限（{@code dailyLimit}），超限保留 L4 结论并标注</li>
 *   <li>会诊失败/异常不丢 L4 结论，仅追加标注</li>
 * </ul>
 *
 * <p>线程安全：日计数器以 synchronized 保护；按设计仅分析线程单线程调用。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public class L4SupervisorLayer implements AnalysisLayer {

    private static final Logger log = LoggerFactory.getLogger(L4SupervisorLayer.class);

    /** 会诊结论段落标记。 */
    private static final String MARK_CONSULT = "\n\n【多 Agent 会诊】\n";

    /** 降级标注前缀。 */
    private static final String MARK_DEGRADE = "\n\n【会诊降级】";

    private final SupervisorAgent supervisorAgent;
    private final int dailyLimit;
    private final Clock clock;

    /** 当日已会诊次数（跨日自动清零）。 */
    private int dailyCount;

    /** 计数所属日期。 */
    private LocalDate countDate;

    /**
     * 构造 L5 Supervisor 会诊层。
     *
     * @param supervisorAgent Supervisor 编排器
     * @param dailyLimit      每日会诊调用上限（正数）
     * @param clock           时钟（日界判定，可注入测试时钟）
     */
    public L4SupervisorLayer(SupervisorAgent supervisorAgent, int dailyLimit, Clock clock) {
        this.supervisorAgent = supervisorAgent;
        this.dailyLimit = dailyLimit;
        this.clock = clock;
        this.countDate = LocalDate.now(clock);
    }

    @Override
    public int order() {
        return 5;
    }

    @Override
    public AnalysisOutcome apply(AnalysisContext context) {
        context.markLayerReached(5);
        if (!context.isEscalate()) {
            return AnalysisOutcome.complete();
        }
        if (!tryAcquireDaily()) {
            appendDegrade(context, "超过每日会诊上限 " + dailyLimit + " 次，保留 L4 结论");
            return AnalysisOutcome.complete();
        }

        String prompt = buildPrompt(context);
        String conversationId = "logwatch-" + context.getFingerprint().substring(0, 12);
        try {
            AgentExecutionResult result = supervisorAgent.orchestrate(prompt, conversationId);
            if (result.success()) {
                context.setAnalysis(context.getAnalysis() + MARK_CONSULT + result.answer());
            } else {
                appendDegrade(context, "会诊执行失败：" + result.errorMessage());
            }
        } catch (RuntimeException e) {
            log.warn("Supervisor 会诊异常，保留 L4 结论: {}", e.toString());
            appendDegrade(context, "会诊调用异常");
        }
        return AnalysisOutcome.complete();
    }

    /**
     * 拼装会诊请求：日志来源/级别/发生次数 + L4 初步结论 + 知识参考。
     */
    private String buildPrompt(AnalysisContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("生产日志告警需要多 Agent 联合会诊。\n");
        sb.append("日志来源：").append(context.getEvent().source()).append('\n');
        sb.append("告警级别：").append(context.getLevel()).append('\n');
        sb.append("窗口内发生次数：").append(context.getOccurrence()).append('\n');
        sb.append("错误日志：\n").append(context.getEvent().content()).append('\n');
        if (context.getAnalysis() != null) {
            sb.append("初步分析：").append(context.getAnalysis()).append('\n');
        }
        if (!context.getKnowledgeRefs().isEmpty()) {
            sb.append("相关知识库条目：").append(String.join("、", context.getKnowledgeRefs())).append('\n');
        }
        sb.append("请给出根因结论与处置建议。");
        return sb.toString();
    }

    /**
     * 追加会诊降级标注。
     */
    private void appendDegrade(AnalysisContext context, String reason) {
        String analysis = context.getAnalysis() == null ? "" : context.getAnalysis();
        context.setAnalysis(analysis + MARK_DEGRADE + reason);
    }

    /**
     * 日限流：跨日清零，超限返回 false，否则计数加一。
     */
    private synchronized boolean tryAcquireDaily() {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(countDate)) {
            countDate = today;
            dailyCount = 0;
        }
        if (dailyCount >= dailyLimit) {
            return false;
        }
        dailyCount++;
        return true;
    }
}
