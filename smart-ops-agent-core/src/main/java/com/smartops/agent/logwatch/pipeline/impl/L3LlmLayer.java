package com.smartops.agent.logwatch.pipeline.impl;

import com.smartops.agent.logwatch.pipeline.AnalysisLayer;
import com.smartops.agent.logwatch.StackTraceParser;
import com.smartops.common.exception.LlmCallException;
import com.smartops.domain.logwatch.AnalysisContext;
import com.smartops.domain.logwatch.AnalysisOutcome;
import com.smartops.infrastructure.chat.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * L4 LLM 单 Agent 根因分析层。
 *
 * <p>经 {@link ChatService#chatWithSystemPrompt} 让 LLM 对错误日志做
 * 根因分析并给出解决建议，响应约定结构：</p>
 * <pre>【原因分析】…【解决建议】…（可选）【需会诊】</pre>
 *
 * <p>成本与稳定性护栏：</p>
 * <ul>
 *   <li>分钟级滑动窗限流（{@code ratePerMinute}），超限不调 LLM 直接降级</li>
 *   <li>{@link LlmCallException} 捕获后降级为规则结果落库（标注"LLM 降级"）</li>
 *   <li>LLM 输出【需会诊】或同指纹合并次数达阈值 → 标记升级，放行 L5</li>
 * </ul>
 *
 * <p>线程安全：限流状态以 synchronized 保护；其余无状态。
 * 按设计仅分析线程单线程调用。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public class L3LlmLayer implements AnalysisLayer {

    private static final Logger log = LoggerFactory.getLogger(L3LlmLayer.class);

    /** 结构化标记：原因分析段。 */
    private static final String MARK_ANALYSIS = "【原因分析】";

    /** 结构化标记：解决建议段。 */
    private static final String MARK_SUGGESTION = "【解决建议】";

    /** 结构化标记：需会诊（升级 Supervisor）。 */
    private static final String MARK_ESCALATE = "【需会诊】";

    /** 限流窗口长度。 */
    private static final Duration RATE_WINDOW = Duration.ofMinutes(1);

    /** 注入 LLM 的日志内容最大字符数（控制 token 成本）。 */
    private static final int CONTENT_MAX_LENGTH = 2000;

    private final ChatService chatService;
    private final String systemPrompt;
    private final int ratePerMinute;
    private final int escalateOccurrenceThreshold;
    private final Clock clock;

    /** 滑动窗口内的 LLM 调用时间戳。 */
    private final Deque<Instant> callTimestamps = new ArrayDeque<>();

    /**
     * 构造 L4 LLM 分析层。
     *
     * @param chatService                  LLM 调用入口
     * @param systemPrompt                 角色化系统提示词（外置文件加载）
     * @param ratePerMinute                每分钟 LLM 调用上限（正数）
     * @param escalateOccurrenceThreshold  同指纹合并次数达到该值时升级 L5
     * @param clock                        时钟（限流窗口计时，可注入测试时钟）
     */
    public L3LlmLayer(ChatService chatService, String systemPrompt,
                      int ratePerMinute, int escalateOccurrenceThreshold, Clock clock) {
        this.chatService = chatService;
        this.systemPrompt = systemPrompt;
        this.ratePerMinute = ratePerMinute;
        this.escalateOccurrenceThreshold = escalateOccurrenceThreshold;
        this.clock = clock;
    }

    @Override
    public int order() {
        return 4;
    }

    @Override
    public AnalysisOutcome apply(AnalysisContext context) {
        context.markLayerReached(4);

        if (!tryAcquire()) {
            degrade(context, "LLM 分析限流降级（每分钟上限 " + ratePerMinute + " 次）");
            return AnalysisOutcome.complete();
        }

        String response;
        try {
            response = chatService.chatWithSystemPrompt(systemPrompt, buildUserMessage(context));
        } catch (LlmCallException e) {
            log.warn("LLM 根因分析失败，降级落库: {}", e.toString());
            degrade(context, "LLM 调用失败降级");
            return AnalysisOutcome.complete();
        }

        parseResponse(response, context);
        if (response.contains(MARK_ESCALATE)
                || context.getOccurrence() >= escalateOccurrenceThreshold) {
            context.markEscalate();
            return AnalysisOutcome.proceed();
        }
        return AnalysisOutcome.complete();
    }

    /**
     * 拼装注入 LLM 的用户消息：日志内容（截断）+ L3 知识参考。
     */
    private String buildUserMessage(AnalysisContext context) {
        StringBuilder sb = new StringBuilder();
        String content = context.getEvent().content();
        sb.append("【错误日志】\n")
                .append(content.length() <= CONTENT_MAX_LENGTH
                        ? content
                        : content.substring(0, CONTENT_MAX_LENGTH));
        List<String> refs = context.getKnowledgeRefs();
        if (!refs.isEmpty()) {
            sb.append("\n\n【相关知识库条目】\n");
            for (String ref : refs) {
                sb.append("- ").append(ref).append('\n');
            }
        }
        sb.append("\n发生次数：").append(context.getOccurrence());
        sb.append("\n日志来源：").append(context.getEvent().source());
        // 阶段七：堆栈解析 → 代码定位
        StackTraceParser parser = new StackTraceParser();
        java.util.List<StackTraceParser.CodeLocation> locs = parser.parse(content);
        if (!locs.isEmpty()) {
            sb.append("\n\n【代码定位】\n");
            for (int i = 0; i < Math.min(locs.size(), 5); i++) {
                StackTraceParser.CodeLocation loc = locs.get(i);
                sb.append(i + 1).append(". ")
                  .append(loc.className()).append(" (")
                  .append(loc.fileName()).append(":")
                  .append(loc.lineNumber()).append(")\n");
            }
        }
        return sb.toString();
    }

    /**
     * 解析 LLM 结构化响应：无标记时整体作为分析结论。
     */
    private void parseResponse(String response, AnalysisContext context) {
        int analysisIdx = response.indexOf(MARK_ANALYSIS);
        if (analysisIdx < 0) {
            context.setAnalysis(response.trim());
            context.setSuggestion("");
            return;
        }
        int suggestionIdx = response.indexOf(MARK_SUGGESTION);
        if (suggestionIdx < 0) {
            context.setAnalysis(response.substring(analysisIdx + MARK_ANALYSIS.length()).trim());
            context.setSuggestion("");
            return;
        }
        context.setAnalysis(response
                .substring(analysisIdx + MARK_ANALYSIS.length(), suggestionIdx).trim());
        String suggestion = response.substring(suggestionIdx + MARK_SUGGESTION.length())
                .replace(MARK_ESCALATE, "").trim();
        context.setSuggestion(suggestion);
    }

    /**
     * 降级：以 L1 规则结果生成分析结论与人工排查建议。
     */
    private void degrade(AnalysisContext context, String reason) {
        String firstLine = context.getEvent().content().split("\n", 2)[0];
        context.setAnalysis("（" + reason + "）规则定级 "
                + context.getLevel() + "：" + firstLine);
        context.setSuggestion("请人工检查日志来源 " + context.getEvent().source());
    }

    /**
     * 滑动窗限流：窗口内调用数达上限返回 false，否则登记本次调用。
     */
    private synchronized boolean tryAcquire() {
        Instant now = clock.instant();
        Instant threshold = now.minus(RATE_WINDOW);
        while (!callTimestamps.isEmpty() && callTimestamps.peekFirst().isBefore(threshold)) {
            callTimestamps.pollFirst();
        }
        if (callTimestamps.size() >= ratePerMinute) {
            return false;
        }
        callTimestamps.addLast(now);
        return true;
    }
}
