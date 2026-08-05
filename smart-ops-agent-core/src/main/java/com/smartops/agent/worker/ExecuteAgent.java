package com.smartops.agent.worker;

import com.smartops.agent.a2a.AgentCardRegistry;
import com.smartops.agent.security.SecurityGate;
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
 * 执行 Agent。
 *
 * <p>阶段三 Worker Agent 之一，负责自动化运维操作（重启、扩缩容、配置变更）。
 * 高风险操作：执行前必须经过 {@link SecurityGate} 安全门校验——
 * 高危操作缺少人工确认时抛出安全违规异常，由 API 层发起一次性令牌确认流程。</p>
 *
 * <p>安全门放行后，由 LLM 生成操作方案（步骤、预期影响、回滚方案，
 * 系统提示词外置于 classpath:prompts/worker-execute.txt）。
 * <b>注意：当前阶段实际执行仍为模拟</b>——返回的是 LLM 生成的方案文本，
 * 不真正执行任何运维操作；真实执行通道待后续阶段落地。</p>
 *
 * <p>支持的意图：{@link IntentType#EXECUTE_OPERATION}。</p>
 *
 * <p>线程安全：依赖组件线程安全，本组件无状态。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Component
public class ExecuteAgent extends AbstractWorkerAgent {

    private static final Logger log = LoggerFactory.getLogger(ExecuteAgent.class);

    /** 角色专属系统提示词（外置，类加载时加载一次）。 */
    private static final String EXECUTE_PROMPT = loadPromptTemplate("prompts/worker-execute.txt");

    /** 安全门：高危操作执行前的人工确认校验。 */
    private final SecurityGate securityGate;

    /**
     * 构造执行 Agent。
     *
     * @param registry     Agent Card 注册中心
     * @param securityGate 安全门（高危操作确认校验）
     * @param chatService  LLM 对话服务
     */
    public ExecuteAgent(AgentCardRegistry registry, SecurityGate securityGate, ChatService chatService) {
        super(buildCard(), registry, chatService);
        this.securityGate = securityGate;
    }

    /**
     * 构建执行 Agent 的能力卡片。
     */
    private static AgentCard buildCard() {
        return new AgentCard(
                "execute-agent",
                AgentRole.EXECUTE,
                "执行Agent",
                "自动化运维操作（重启、扩缩容、配置变更）",
                Set.of("restart", "scaling", "config", "deployment"),
                Set.of(IntentType.EXECUTE_OPERATION),
                2
        );
    }

    @Override
    protected A2aResponse doHandle(A2aRequest request) {
        log.info("执行 Agent 处理指令: taskId={}, instruction={}",
                request.taskId(), request.instruction());

        // 安全门：高危操作（重启/扩缩容/配置变更等）需人工确认后放行，
        // 未确认时抛出 SecurityViolationException，由 API 层发起令牌确认流程
        securityGate.checkPermitted(request.instruction());

        // 安全门放行后由 LLM 生成操作方案（当前阶段实际执行为模拟，见类 Javadoc）
        String result = chatWithRolePrompt(EXECUTE_PROMPT, request);

        return A2aResponse.success(request.requestId(), request.taskId(),
                AgentRole.EXECUTE, result);
    }
}
