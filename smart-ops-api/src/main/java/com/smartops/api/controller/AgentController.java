package com.smartops.api.controller;

import com.smartops.api.dto.ChatRequest;
import com.smartops.api.dto.ChatResponse;
import com.smartops.agent.router.AgentRouter;
import com.smartops.agent.security.ConfirmationContext;
import com.smartops.agent.security.ConfirmationTokenStore;
import com.smartops.agent.security.InputFilter;
import com.smartops.common.exception.SecurityViolationException;
import com.smartops.common.model.AgentExecutionResult;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Agent 对话 Controller。
 *
 * <p>阶段二集成 AgentRouter 后，对话流程升级为：
 * 意图识别 → 任务复杂度分析 → 模式选择（ReAct / Plan-and-Solve）→ 执行 → 返回结果。</p>
 *
 * <p>对话流程：
 * <ol>
 *   <li>接收用户消息与会话 ID（可选）</li>
 *   <li>若会话 ID 为空，生成新 UUID 作为会话 ID</li>
 *   <li>调用 {@link AgentRouter#route} 进行路由决策与执行</li>
 *   <li>将 {@link AgentExecutionResult} 映射为 {@link ChatResponse} 返回</li>
 * </ol></p>
 *
 * <p>线程安全：Controller 单例，依赖的 AgentRouter 线程安全。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    /**
     * AgentRouter：路由决策引擎，负责意图识别、模式选择与执行编排。
     */
    private final AgentRouter agentRouter;

    /**
     * 一次性人工确认令牌存储：高危操作被安全门拦截后签发令牌，
     * 客户端携带令牌重提时验证并消费。
     */
    private final ConfirmationTokenStore confirmationTokenStore;

    /** L0 输入过滤器（阶段五安全模型），未配置开关关闭时为 null。 */
    private final InputFilter inputFilter;

    /**
     * 构造 AgentController。
     *
     * @param agentRouter            Spring 容器注入的路由决策引擎
     * @param confirmationTokenStore 人工确认令牌存储
     * @param inputFilterProvider    L0 输入过滤器提供者（null 时跳过过滤）
     */
    public AgentController(AgentRouter agentRouter,
                           ConfirmationTokenStore confirmationTokenStore,
                           ObjectProvider<InputFilter> inputFilterProvider) {
        this.agentRouter = agentRouter;
        this.confirmationTokenStore = confirmationTokenStore;
        this.inputFilter = inputFilterProvider.getIfAvailable();
    }

    /**
     * 同步对话接口。
     *
     * <p>用户发送运维问题，Agent 通过路由决策引擎自动选择最优执行模式，
     * 完成意图识别、工具调用、结果生成后返回。</p>
     *
     * @param request 对话请求，包含会话 ID（可选）和用户消息
     * @return 对话响应，包含会话 ID、回复内容和执行元数据
     */
    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        // 会话 ID 为空时生成新 UUID，用于关联短期记忆
        String conversationId = request.conversationId() != null && !request.conversationId().isBlank()
                ? request.conversationId()
                : UUID.randomUUID().toString();

        log.info("收到对话请求 conversationId={}, hasConfirmationToken={}", conversationId,
                request.confirmationToken() != null && !request.confirmationToken().isBlank());

        // 携带确认令牌的重提请求：验证令牌（绑定会话与原始消息），通过后标记当前线程已确认；
        // 令牌一次性，验证即消费
        if (request.confirmationToken() != null && !request.confirmationToken().isBlank()) {
            boolean confirmed = confirmationTokenStore.validateAndConsume(
                    request.confirmationToken(), conversationId, request.message());
            if (!confirmed) {
                log.warn("确认令牌验证失败 conversationId={}", conversationId);
                return ChatResponse.failure(conversationId, null,
                        "确认令牌无效、已过期或与待确认操作不匹配，请重新发起操作");
            }
            ConfirmationContext.markConfirmed();
        }

        try {
            // L0 输入安全过滤（阶段五安全模型）
            String message = inputFilter != null
                    ? inputFilter.filter(request.message()) : request.message();

            // 调用路由决策引擎：意图识别 → 复杂度分析 → 模式选择 → 执行
            AgentExecutionResult result = agentRouter.route(message, conversationId);

            log.info("对话完成 conversationId={}, mode={}, success={}, iterations={}",
                    conversationId, result.mode(), result.success(), result.iterations());

            // 映射执行结果为 API 响应
            return new ChatResponse(
                    conversationId,
                    result.answer(),
                    LocalDateTime.now(),
                    result.mode(),
                    result.iterations(),
                    result.success(),
                    result.errorMessage(),
                    false,
                    null
            );
        } catch (SecurityViolationException e) {
            String token = confirmationTokenStore.issue(conversationId, request.message());
            log.info("高危操作待人工确认 conversationId={}, reason={}", conversationId, e.getMessage());
            return ChatResponse.pendingConfirmation(conversationId, token,
                    "该操作属于高风险操作，需人工确认后执行。请携带确认令牌重新提交相同请求。详情: "
                            + e.getMessage());
        } catch (Exception e) {
            log.error("对话处理异常 conversationId={}", conversationId, e);
            return ChatResponse.failure(conversationId, null,
                    "系统处理异常，请稍后重试: " + e.getMessage());
        } finally {
            // 清除线程确认标记，防止线程池复用导致标记泄露到后续请求
            ConfirmationContext.clear();
        }
    }
}
