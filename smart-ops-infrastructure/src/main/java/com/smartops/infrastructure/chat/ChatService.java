package com.smartops.infrastructure.chat;

import com.smartops.common.exception.LlmCallException;
import com.smartops.common.exception.RateLimitException;
import com.smartops.infrastructure.llm.LlmProvider;
import com.smartops.infrastructure.llm.LlmProviderRegistry;
import com.smartops.infrastructure.llm.SlidingWindowRateLimiter;
import com.smartops.infrastructure.observability.Observability;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * LLM 对话服务封装。
 *
 * <p>封装 Spring AI {@link ChatClient} 的 fluent API，提供简洁的调用接口。
 * 目的是降低上层组件（ReActExecutor、PlanGenerator 等）对 ChatClient
 * fluent API 的直接依赖，使单元测试只需 Mock 本接口即可。</p>
 *
 * <p><b>会话记忆隔离设计</b>（修复跨用户上下文泄露缺陷）：
 * <ul>
 *   <li>带 {@code conversationId} 参数的方法走记忆客户端，
 *       每次调用设置 {@link ChatMemory#CONVERSATION_ID}，不同会话的记忆严格隔离</li>
 *   <li>不带 {@code conversationId} 的方法是<b>无状态调用</b>（走无记忆客户端），
 *       用于意图识别、计划生成等元调用——既不读取也不写入任何会话记忆，
 *       从机制上杜绝用户输入经元调用泄露到其他会话</li>
 * </ul></p>
 *
 * <p><b>多模型 Provider 路由</b>（阶段五多模型抽象层）：
 * 当 {@link LlmProviderRegistry} Bean 存在时，无状态路径自动选择
 * {@link LlmProviderRegistry#getToolCapable()}（有工具时）或
 * {@link LlmProviderRegistry#getDefault()}（无工具时）的 ChatClient；
 * 记忆路径沿用原 {@code memoryChatClient}（依赖 MessageChatMemoryAdvisor
 * 预装 Advisor）。温度按 Provider 的 [min, max] 区间钳位。</p>
 *
 * <p><b>异常分层</b>：LLM 调用抛出的原始运行时异常统一包装为
 * {@link LlmCallException}（错误码 {@code LLM_CALL_FAILED}），
 * 上层组件据此类型化捕获，符合 agent.md 第五章 5.3 节异常分层规范。</p>
 *
 * <p>线程安全：ChatClient 本身线程安全，本服务无状态。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    /** 带记忆 Advisor 的客户端（默认系统提示词 + MessageChatMemoryAdvisor）。 */
    private final ChatClient memoryChatClient;

    /** 无记忆的客户端，用于元调用（意图识别、计划生成等），不参与任何会话记忆。 */
    private final ChatClient statelessChatClient;

    /** 可观测性门面（指标+审计），可为 null（测试或裁剪场景）。 */
    private final Observability observability;

    /** 多模型 Provider 注册表，可为 null（未配置多模型时）。 */
    private final LlmProviderRegistry registry;

    /** LLM 调用滑动窗口限流器，可为 null（限流配置为 0 时禁用）。 */
    private final SlidingWindowRateLimiter rateLimiter;

    /** 熔断器，可为 null（未配置时）。 */
    private final CircuitBreaker circuitBreaker;

    /**
     * 构造 ChatService（无可观测性、无 Provider 注册表，供测试使用）。
     *
     * @param memoryChatClient   Spring 容器注入的 ChatClient（带记忆 Advisor）
     * @param chatClientBuilder  自动配置的 Builder，用于构建无记忆客户端
     */
    public ChatService(ChatClient memoryChatClient, ChatClient.Builder chatClientBuilder) {
        this(memoryChatClient, chatClientBuilder, null, null, null, 0);
    }

    /**
     * 构造 ChatService（无 Provider 注册表）。
     */
    public ChatService(ChatClient memoryChatClient, ChatClient.Builder chatClientBuilder,
                       Observability observability) {
        this(memoryChatClient, chatClientBuilder, observability, null, null, 0);
    }

    /**
     * 构造 ChatService（多模型 Provider 支持，Spring 装配入口）。
     *
     * <p>类内存在多个构造器，须以 {@link Autowired} 显式标注注入入口。</p>
     *
     * @param memoryChatClient   Spring 容器注入的 ChatClient（带记忆 Advisor）
     * @param chatClientBuilder  自动配置的 Builder，用于构建无记忆客户端
     * @param observability      可观测性门面（LLM 调用指标+审计），可为 null
     * @param registryProvider   多模型 Provider 注册表提供者（无配置时 null）
     * @param circuitBreakerProvider 熔断器提供者（无配置时 null）
     * @param ratePerMinute      LLM 调用每分钟上限，0 或负数表示不限流
     */
    @Autowired
    public ChatService(ChatClient memoryChatClient, ChatClient.Builder chatClientBuilder,
                       Observability observability,
                       ObjectProvider<LlmProviderRegistry> registryProvider,
                       ObjectProvider<CircuitBreaker> circuitBreakerProvider,
                       @Value("${smartops.llm.rate-per-minute:30}") int ratePerMinute) {
        this.memoryChatClient = memoryChatClient;
        this.statelessChatClient = chatClientBuilder.build();
        this.observability = observability;
        this.registry = registryProvider != null ? registryProvider.getIfAvailable() : null;
        this.circuitBreaker = circuitBreakerProvider != null
                ? circuitBreakerProvider.getIfAvailable() : null;
        this.rateLimiter = ratePerMinute > 0
                ? new SlidingWindowRateLimiter(ratePerMinute, Duration.ofMinutes(1), Clock.systemUTC())
                : null;
    }

    // ==================== 无状态调用（元调用：意图识别 / 计划生成 / 总结） ====================

    /**
     * 无状态简单对话：不读写任何会话记忆。
     *
     * @param userMessage 用户消息
     * @return LLM 回复内容
     * @throws IllegalArgumentException 当消息为 null 或空白时
     */
    public String chat(String userMessage) {
        validateMessage(userMessage);
        log.debug("发送无状态对话请求: length={}", userMessage.length());
        ChatClient client = statelessClient(false);
        return invokeLlm(() -> client.prompt()
                .user(userMessage)
                .call()
                .content());
    }

    /**
     * 无状态带工具对话：不读写任何会话记忆。
     *
     * @param userMessage 用户消息
     * @param toolBeans   工具 Bean 数组（含 @Tool 注解方法）
     * @return LLM 回复内容
     * @throws IllegalArgumentException 当消息为 null 或空白时
     */
    public String chatWithTools(String userMessage, Object... toolBeans) {
        validateMessage(userMessage);
        boolean hasTools = toolBeans != null && toolBeans.length > 0;
        log.debug("发送无状态带工具对话请求: length={}, toolCount={}",
                userMessage.length(), hasTools ? toolBeans.length : 0);
        ChatClient client = statelessClient(hasTools);
        return invokeLlm(() -> {
            var request = client.prompt().user(userMessage);
            if (hasTools) {
                request = request.tools(toolBeans);
            }
            return request.call().content();
        });
    }

    /**
     * 无状态带自定义系统提示词对话：不读写任何会话记忆。
     *
     * <p>命名区别于会话级 {@link #chat(String, String)}：两参字符串调用若同名
     * 会产生重载歧义（系统提示词被误绑为会话 ID），故独立命名。</p>
     *
     * @param systemPrompt 自定义系统提示词
     * @param userMessage  用户消息
     * @param toolBeans    工具 Bean 数组，可为空
     * @return LLM 回复内容
     * @throws IllegalArgumentException 当系统提示词或消息为 null/空白时
     */
    public String chatWithSystemPrompt(String systemPrompt, String userMessage, Object... toolBeans) {
        validateMessage(systemPrompt);
        validateMessage(userMessage);
        boolean hasTools = toolBeans != null && toolBeans.length > 0;
        log.debug("发送无状态带系统提示对话: systemPromptLength={}, messageLength={}, toolCount={}",
                systemPrompt.length(), userMessage.length(),
                hasTools ? toolBeans.length : 0);
        ChatClient client = statelessClient(hasTools);
        return invokeLlm(() -> {
            var request = client.prompt()
                    .system(systemPrompt)
                    .user(userMessage);
            if (hasTools) {
                request = request.tools(toolBeans);
            }
            return request.call().content();
        });
    }

    // ==================== 会话级调用（主对话路径，记忆按 conversationId 隔离） ====================

    /**
     * 会话级对话：读写指定会话的短期记忆。
     *
     * @param conversationId 会话 ID，记忆隔离的边界，不能为空
     * @param userMessage    用户消息
     * @return LLM 回复内容
     * @throws IllegalArgumentException 当会话 ID 或消息为 null/空白时
     */
    public String chat(String conversationId, String userMessage) {
        validateConversationId(conversationId);
        validateMessage(userMessage);
        log.debug("发送会话对话请求: conversationId={}, length={}", conversationId, userMessage.length());

        return invokeLlm(() -> memoryChatClient.prompt()
                .user(userMessage)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content());
    }

    /**
     * 会话级带工具对话：读写指定会话的短期记忆（供 ReAct 等主执行路径使用）。
     *
     * <p>需工具调用时，若多模型 Provider 注册表可用，自动选择首个
     * {@code supportsTools=true} 的 Provider 对应的 ChatClient；否则沿用原记忆客户端。</p>
     *
     * @param conversationId 会话 ID，记忆隔离的边界，不能为空
     * @param userMessage    用户消息
     * @param toolBeans      工具 Bean 数组
     * @return LLM 回复内容
     * @throws IllegalArgumentException 当会话 ID 或消息为 null/空白时
     */
    public String chatWithTools(String conversationId, String userMessage, Object... toolBeans) {
        validateConversationId(conversationId);
        validateMessage(userMessage);
        boolean hasTools = toolBeans != null && toolBeans.length > 0;
        log.debug("发送会话带工具对话请求: conversationId={}, length={}, toolCount={}",
                conversationId, userMessage.length(), hasTools ? toolBeans.length : 0);
        ChatClient client = memoryClientForTools(hasTools);
        return invokeLlm(() -> {
            var request = client.prompt()
                    .user(userMessage)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId));
            if (hasTools) {
                request = request.tools(toolBeans);
            }
            return request.call().content();
        });
    }

    /**
     * 会话级带自定义系统提示词对话：读写指定会话的短期记忆。
     *
     * @param conversationId 会话 ID，记忆隔离的边界，不能为空
     * @param systemPrompt   自定义系统提示词
     * @param userMessage    用户消息
     * @param toolBeans      工具 Bean 数组，可为空
     * @return LLM 回复内容
     * @throws IllegalArgumentException 当会话 ID、系统提示词或消息为 null/空白时
     */
    public String chat(String conversationId, String systemPrompt, String userMessage, Object... toolBeans) {
        validateConversationId(conversationId);
        validateMessage(systemPrompt);
        validateMessage(userMessage);
        log.debug("发送会话带系统提示对话: conversationId={}, systemPromptLength={}, messageLength={}",
                conversationId, systemPrompt.length(), userMessage.length());

        return invokeLlm(() -> {
            var request = memoryChatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId));
            if (toolBeans != null && toolBeans.length > 0) {
                request = request.tools(toolBeans);
            }
            return request.call().content();
        });
    }

    /**
     * 无状态路径：按是否需要工具选择 Provider，返回对应 ChatClient。
     *
     * <p>包私有以允许测试直接验证路由逻辑。</p>
     */
    ChatClient statelessClient(boolean needsTools) {
        if (registry == null) {
            return statelessChatClient;
        }
        return selectProvider(needsTools).chatClient();
    }

    /**
     * 记忆路径（带工具场景）：选择工具能力 Provider，但保留原 memory client 的 Advisor。
     *
     * <p>当 registry 存在且需要工具时，用工具 Provider 的 chatClient 替代
     * memoryChatClient 本身已有 MessageChatMemoryAdvisor + ToolCallingAdvisor，
     * 故记忆路径目前仅在 registry 可用且 needsTools 时替换。
     * 纯文本记忆路径仍走原 memoryChatClient。</p>
     */
    ChatClient memoryClientForTools(boolean needsTools) {
        if (registry != null && needsTools) {
            LlmProvider provider = registry.getToolCapable().orElse(registry.getDefault());
            return provider.chatClient();
        }
        return memoryChatClient;
    }

    /**
     * 按是否需要工具选择 Provider。
     */
    private LlmProvider selectProvider(boolean needsTools) {
        if (needsTools) {
            return registry.getToolCapable().orElse(registry.getDefault());
        }
        return registry.getDefault();
    }

    /**
     * 执行 LLM 调用并统一异常翻译。
     *
     * <p>将 Spring AI / HTTP 层抛出的原始运行时异常包装为
     * {@link LlmCallException}（保留原始消息与根因），使上层组件可以
     * 类型化捕获平台异常而非裸 catch Exception。已是平台异常的直接重抛，
     * 不做二次包装。</p>
     *
     * @param call LLM 调用（fluent 链路末端 call().content()）
     * @return LLM 回复内容
     * @throws LlmCallException 当 LLM 调用失败时
     */
    String invokeLlm(Supplier<String> call) {
        if (rateLimiter != null && !rateLimiter.tryAcquire()) {
            throw new RateLimitException("LLM 调用超出限流上限");
        }
        if (circuitBreaker != null && !circuitBreaker.tryAcquirePermission()) {
            throw new LlmCallException("LLM 服务熔断保护已开启，请稍后重试");
        }
        long startNanos = System.nanoTime();
        try {
            String result = call.get();
            if (circuitBreaker != null) {
                circuitBreaker.onSuccess(System.nanoTime() - startNanos,
                        java.util.concurrent.TimeUnit.NANOSECONDS);
            }
            observeLlm(true, startNanos, result);
            return result;
        } catch (LlmCallException e) {
            if (circuitBreaker != null) {
                circuitBreaker.onError(System.nanoTime() - startNanos,
                        java.util.concurrent.TimeUnit.NANOSECONDS, e);
            }
            observeLlm(false, startNanos, e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            if (circuitBreaker != null) {
                circuitBreaker.onError(System.nanoTime() - startNanos,
                        java.util.concurrent.TimeUnit.NANOSECONDS, e);
            }
            LlmCallException wrapped = new LlmCallException("LLM 调用失败: " + e.getMessage(), e);
            observeLlm(false, startNanos, wrapped.getMessage());
            throw wrapped;
        }
    }

    /**
     * LLM 调用观测（指标+审计）：observability 缺失时静默跳过。
     */
    private void observeLlm(boolean success, long startNanos, String detail) {
        if (observability != null) {
            observability.recordLlmCall(success,
                    (System.nanoTime() - startNanos) / 1_000_000, detail);
        }
    }

    /**
     * 校验消息不为 null 或空白。
     *
     * @param message 待校验消息
     * @throws IllegalArgumentException 当消息为 null 或空白时
     */
    private void validateMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("消息不能为 null 或空白");
        }
    }

    /**
     * 校验会话 ID 不为 null 或空白。会话 ID 是记忆隔离的边界，
     * 缺失会导致记忆写入默认会话，造成跨用户上下文泄露。
     *
     * @param conversationId 待校验会话 ID
     * @throws IllegalArgumentException 当会话 ID 为 null 或空白时
     */
    private void validateConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("会话 ID 不能为 null 或空白");
        }
    }
}
