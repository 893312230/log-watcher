package com.smartops.agent.worker;

import com.smartops.agent.a2a.AgentCardRegistry;
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
 * 分析 Agent。
 *
 * <p>阶段三 Worker Agent 之一，负责根因分析、日志分析、异常检测。
 * 通过 {@link ChatService} 调用 LLM 对任务上下文做语义推理，
 * 生成结构化分析报告（系统提示词外置于 classpath:prompts/worker-analyze.txt）。</p>
 *
 * <p>支持的意图：{@link IntentType#ROOT_CAUSE}、{@link IntentType#ANALYZE_ALERT}。</p>
 *
 * <p>线程安全：依赖组件线程安全，本组件无状态。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Component
public class AnalyzeAgent extends AbstractWorkerAgent {

    private static final Logger log = LoggerFactory.getLogger(AnalyzeAgent.class);

    /** 角色专属系统提示词（外置，类加载时加载一次）。 */
    private static final String ANALYZE_PROMPT = loadPromptTemplate("prompts/worker-analyze.txt");

    /**
     * 构造分析 Agent。
     *
     * @param registry    Agent Card 注册中心
     * @param chatService LLM 对话服务
     */
    public AnalyzeAgent(AgentCardRegistry registry, ChatService chatService) {
        super(buildCard(), registry, chatService);
    }

    /**
     * 构建分析 Agent 的能力卡片。
     */
    private static AgentCard buildCard() {
        return new AgentCard(
                "analyze-agent",
                AgentRole.ANALYZE,
                "分析Agent",
                "根因分析、日志分析、异常检测",
                Set.of("root-cause", "logs", "traces", "anomaly-detection"),
                Set.of(IntentType.ROOT_CAUSE, IntentType.ANALYZE_ALERT),
                3
        );
    }

    @Override
    protected A2aResponse doHandle(A2aRequest request) {
        log.info("分析 Agent 处理指令: taskId={}, instruction={}",
                request.taskId(), request.instruction());

        // LLM 上下文分析：基于指令（可含上游监控结论）生成结构化分析报告
        String result = chatWithRolePrompt(ANALYZE_PROMPT, request);

        return A2aResponse.success(request.requestId(), request.taskId(),
                AgentRole.ANALYZE, result);
    }
}
