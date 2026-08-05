package com.smartops.agent.worker;

import com.smartops.agent.a2a.AgentCardRegistry;
import com.smartops.agent.tools.PrometheusTools;
import com.smartops.common.enums.AgentRole;
import com.smartops.common.enums.IntentType;
import com.smartops.common.model.A2aRequest;
import com.smartops.common.model.A2aResponse;
import com.smartops.common.model.AgentCard;
import com.smartops.infrastructure.chat.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 监控 Agent。
 *
 * <p>阶段三 Worker Agent 之一，负责实时监控、告警查询、指标趋势分析。
 * 通过 {@link ChatService} 调用 LLM，并将 {@link PrometheusTools} 作为工具
 * 注入，由 LLM 基于真实指标数据生成监控结论（系统提示词外置于
 * classpath:prompts/worker-monitor.txt）。</p>
 *
 * <p>支持的意图：{@link IntentType#QUERY_METRIC}、{@link IntentType#TREND_ANALYSIS}、
 * {@link IntentType#ANALYZE_ALERT}。</p>
 *
 * <p>线程安全：依赖组件线程安全，本组件无状态。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Component
public class MonitorAgent extends AbstractWorkerAgent {

    private static final Logger log = LoggerFactory.getLogger(MonitorAgent.class);

    /** 角色专属系统提示词（外置，类加载时加载一次）。 */
    private static final String MONITOR_PROMPT = loadPromptTemplate("prompts/worker-monitor.txt");

    /** Prometheus 指标查询工具（作为 LLM 工具注入）。 */
    private final PrometheusTools prometheusTools;

    /**
     * 构造监控 Agent。
     *
     * @param registry         Agent Card 注册中心
     * @param prometheusTools  Prometheus 指标查询工具
     * @param chatService      LLM 对话服务
     */
    public MonitorAgent(AgentCardRegistry registry, PrometheusTools prometheusTools, ChatService chatService) {
        super(buildCard(), registry, chatService);
        this.prometheusTools = prometheusTools;
    }

    /**
     * 构建监控 Agent 的能力卡片。
     */
    private static AgentCard buildCard() {
        return new AgentCard(
                "monitor-agent",
                AgentRole.MONITOR,
                "监控Agent",
                "实时监控、告警查询、指标趋势分析",
                Set.of("prometheus", "metrics", "alerts", "trends"),
                Set.of(IntentType.QUERY_METRIC, IntentType.TREND_ANALYSIS, IntentType.ANALYZE_ALERT),
                5
        );
    }

    @Override
    protected A2aResponse doHandle(A2aRequest request) {
        log.info("监控 Agent 处理指令: taskId={}, instruction={}",
                request.taskId(), request.instruction());

        // LLM + PrometheusTools 工具调用：基于真实指标数据生成监控结论
        String result = chatWithRolePrompt(MONITOR_PROMPT, request, prometheusTools);

        return A2aResponse.success(request.requestId(), request.taskId(),
                AgentRole.MONITOR, result);
    }
}
