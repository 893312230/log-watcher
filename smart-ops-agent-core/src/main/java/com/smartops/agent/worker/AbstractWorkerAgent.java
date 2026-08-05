package com.smartops.agent.worker;

import com.smartops.agent.a2a.AgentCardRegistry;
import com.smartops.common.exception.AgentException;
import com.smartops.common.exception.SecurityViolationException;
import com.smartops.common.model.A2aRequest;
import com.smartops.common.model.A2aResponse;
import com.smartops.common.model.AgentCard;
import com.smartops.infrastructure.chat.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Worker Agent 抽象基类。
 *
 * <p>封装所有 Worker 的公共逻辑：
 * <ul>
 *   <li>构造时向 {@link AgentCardRegistry} 注册能力卡片</li>
 *   <li>请求校验（非 null、目标角色匹配）</li>
 *   <li>异常捕获与失败响应构建</li>
 *   <li>执行日志记录</li>
 *   <li>LLM 调用支持：持有 {@link ChatService}，子类经
 *       {@link #chatWithRolePrompt} 用角色专属系统提示词调用 LLM，
 *       有会话 ID 时走会话记忆，无会话 ID 时降级为无状态调用</li>
 * </ul></p>
 *
 * <p>子类只需实现 {@link #doHandle} 方法，专注业务逻辑。</p>
 *
 * <p>线程安全：依赖组件均线程安全，本类无内部可变状态。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public abstract class AbstractWorkerAgent implements WorkerAgent {

    private static final Logger log = LoggerFactory.getLogger(AbstractWorkerAgent.class);

    /** Agent 能力卡片。 */
    private final AgentCard card;

    /** Agent Card 注册中心。 */
    private final AgentCardRegistry registry;

    /** LLM 对话服务：Worker 生成真实回答的统一入口。 */
    private final ChatService chatService;

    /**
     * 构造 Worker Agent。
     *
     * <p>构造时自动向注册中心注册能力卡片。</p>
     *
     * @param card        能力卡片
     * @param registry    注册中心
     * @param chatService LLM 对话服务
     */
    protected AbstractWorkerAgent(AgentCard card, AgentCardRegistry registry, ChatService chatService) {
        this.card = Objects.requireNonNull(card, "card 不能为 null");
        this.registry = Objects.requireNonNull(registry, "registry 不能为 null");
        this.chatService = Objects.requireNonNull(chatService, "chatService 不能为 null");
        this.registry.register(card);
        log.info("Worker Agent 初始化: agentId={}, role={}", card.agentId(), card.role());
    }

    /**
     * 子类实现的业务处理逻辑。
     *
     * <p>基类已完成请求校验和异常捕获，子类只需关注正常业务流程。
     * 抛出的任何异常都会被基类捕获并转为失败响应。</p>
     *
     * @param request A2A 请求（已校验）
     * @return A2A 响应
     */
    protected abstract A2aResponse doHandle(A2aRequest request);

    @Override
    public final AgentCard getCard() {
        return card;
    }

    @Override
    public final A2aResponse handle(A2aRequest request) {
        Objects.requireNonNull(request, "request 不能为 null");

        log.info("Worker 收到请求: agentId={}, taskId={}, instruction={}",
                card.agentId(), request.taskId(), request.instruction());

        // 校验目标角色是否匹配
        if (request.targetRole() != card.role()) {
            String error = String.format("目标角色不匹配: 期望=%s, 实际=%s",
                    card.role(), request.targetRole());
            log.warn(error);
            return A2aResponse.failure(request.requestId(), request.taskId(),
                    card.role(), error);
        }

        try {
            A2aResponse response = doHandle(request);
            log.info("Worker 处理完成: agentId={}, taskId={}, success={}",
                    card.agentId(), request.taskId(), response.isSuccess());
            return response;
        } catch (SecurityViolationException e) {
            // 安全违规（如高危操作未确认）必须向上传播，
            // 由 API 层发起人工确认流程，不能吞为普通失败响应
            throw e;
        } catch (AgentException e) {
            // 平台异常（如 LLM 调用失败）转为失败响应；
            // 非平台异常属编程错误，向上传播暴露问题
            log.error("Worker 处理异常: agentId={}, taskId={}, error={}",
                    card.agentId(), request.taskId(), e.getMessage(), e);
            return A2aResponse.failure(request.requestId(), request.taskId(),
                    card.role(), "Worker 内部错误: " + e.getMessage());
        }
    }

    /**
     * 获取注册中心（供子类使用）。
     *
     * @return Agent Card 注册中心
     */
    protected final AgentCardRegistry getRegistry() {
        return registry;
    }

    /**
     * 加载角色专属系统提示词模板（外置于 classpath:prompts/）。
     *
     * @param resourcePath 类路径资源路径，如 prompts/worker-monitor.txt
     * @return 提示词文本
     * @throws IllegalStateException 当资源无法读取时
     */
    protected static String loadPromptTemplate(String resourcePath) {
        try {
            return new ClassPathResource(resourcePath).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("无法加载 Worker 提示词: " + resourcePath, e);
        }
    }

    /**
     * 以角色专属系统提示词调用 LLM。
     *
     * <p>请求携带会话 ID 时走会话级调用（读写该会话短期记忆）；
     * 会话 ID 缺失时降级为无状态调用，保证 Worker 在任何分发路径下可用。</p>
     *
     * @param systemPrompt 角色专属系统提示词
     * @param request      A2A 请求（提供指令与会话 ID）
     * @param toolBeans    工具 Bean（如 PrometheusTools），可为空
     * @return LLM 生成的回答
     */
    protected final String chatWithRolePrompt(String systemPrompt, A2aRequest request, Object... toolBeans) {
        if (request.conversationId() != null && !request.conversationId().isBlank()) {
            return chatService.chat(request.conversationId(), systemPrompt, request.instruction(), toolBeans);
        }
        return chatService.chatWithSystemPrompt(systemPrompt, request.instruction(), toolBeans);
    }
}
