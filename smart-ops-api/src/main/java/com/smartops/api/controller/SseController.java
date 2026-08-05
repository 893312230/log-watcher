package com.smartops.api.controller;

import com.smartops.api.dto.ChatRequest;
import com.smartops.agent.tools.PrometheusTools;
import com.smartops.infrastructure.sse.SseTask;
import com.smartops.infrastructure.sse.SseTaskRegistry;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.UUID;

/**
 * SSE 流式对话 Controller（阶段五断线重连）。
 *
 * <p>对应 agent.md 阶段一任务6、阶段五 P12。</p>
 *
 * <p>SSE 推送流程：
 * <ol>
 *   <li>按 conversationId 从 {@link SseTaskRegistry} 查找或创建 {@link SseTask}</li>
 *   <li>若任务已完成 → 直接推送缓存结果 + [DONE]</li>
 *   <li>若任务运行中 → 订阅 SseTask 热流（从连接时刻接收新事件）</li>
 *   <li>空闲期心跳每 15 秒推送 SSE 注释行</li>
 * </ol></p>
 *
 * <p>线程安全：Controller 单例，SseTaskRegistry 线程安全。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/agent")
public class SseController {

    private static final Logger log = LoggerFactory.getLogger(SseController.class);

    public static final String STREAM_DONE_MARKER = "[DONE]";
    public static final String STREAM_ERROR_PREFIX = "[ERROR] ";
    public static final String HEARTBEAT_MARKER = ":heartbeat";

    /** 会话元事件前缀：流首元素回传服务端实际使用的 conversationId。 */
    public static final String CONVERSATION_MARKER = "[CONV] ";

    private static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofSeconds(15);
    private static final int LOG_MESSAGE_MAX_LENGTH = 50;

    private final ChatClient chatClient;
    private final PrometheusTools prometheusTools;
    private final Duration heartbeatInterval;
    private final SseTaskRegistry taskRegistry;
    private final int taskBufferSize;

    /**
     * 构造 SseController（Spring 装配入口）。
     *
     * @param chatClient      ChatClient
     * @param prometheusTools 工具 Bean
     * @param taskRegistryProvider SSE 任务注册表提供者（无 Bean 时用默认）
     * @param bufferSize      每个 SseTask Sinks 缓冲容量
     */
    @Autowired
    public SseController(ChatClient chatClient, PrometheusTools prometheusTools,
                         org.springframework.beans.factory.ObjectProvider<SseTaskRegistry> taskRegistryProvider,
                         @Value("${smartops.sse.task.buffer-size:64}") int bufferSize) {
        this(chatClient, prometheusTools, DEFAULT_HEARTBEAT_INTERVAL,
                taskRegistryProvider.getIfAvailable(), bufferSize);
    }

    /**
     * 构造 SseController（指定心跳间隔，供测试使用）。
     */
    SseController(ChatClient chatClient, PrometheusTools prometheusTools,
                  Duration heartbeatInterval, SseTaskRegistry taskRegistry, int bufferSize) {
        this.chatClient = chatClient;
        this.prometheusTools = prometheusTools;
        this.heartbeatInterval = heartbeatInterval;
        this.taskRegistry = taskRegistry;
        this.taskBufferSize = bufferSize;
    }

    /**
     * SSE 流式对话接口（支持断线重连）。
     *
     * @param request 对话请求
     * @return SSE 事件流
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@Valid @RequestBody ChatRequest request) {
        String conversationId = request.conversationId() != null && !request.conversationId().isBlank()
                ? request.conversationId()
                : UUID.randomUUID().toString();

        log.info("收到流式对话请求 conversationId={}, message={}", conversationId,
                abbreviate(request.message()));

        // 无注册表时回退为原有行为（向后兼容）
        if (taskRegistry == null) {
            return buildLegacyStream(conversationId, request.message());
        }

        SseTask task = taskRegistry.getOrCreate(conversationId,
                () -> new SseTask(taskBufferSize));

        // 已完成 → 直接返回缓存结果
        if (task.isCompleted()) {
            log.info("SSE 任务已完成，返回缓存结果 conversationId={}", conversationId);
            String result = task.finalResult() != null ? task.finalResult() : "";
            return Flux.just(CONVERSATION_MARKER + conversationId, result, STREAM_DONE_MARKER);
        }

        // 新任务 → 启动
        if (task.isNew()) {
            Flux<String> contentFlux = chatClient.prompt()
                    .user(request.message())
                    .tools(prometheusTools)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .stream()
                    .content()
                    .onErrorResume(e -> {
                        log.error("SSE 流式对话异常 conversationId={}", conversationId, e);
                        return Flux.just(STREAM_ERROR_PREFIX + "流式回复生成失败，请稍后重试");
                    })
                    .concatWith(Flux.just(STREAM_DONE_MARKER));
            task.start(contentFlux);
        }

        // 订阅热流 + 心跳（流首元素回传 conversationId，供客户端维持多轮会话）
        Flux<String> heartbeat = Flux.interval(heartbeatInterval)
                .map(tick -> HEARTBEAT_MARKER);
        return Flux.concat(Flux.just(CONVERSATION_MARKER + conversationId),
                Flux.merge(task.stream(), heartbeat));
    }

    /**
     * 无 SseTaskRegistry 时的兼容路径（直接创建请求维度 Flux）。
     */
    private Flux<String> buildLegacyStream(String conversationId, String message) {
        Flux<String> contentFlux = chatClient.prompt()
                .user(message)
                .tools(prometheusTools)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content();

        Flux<String> main = contentFlux
                .onErrorResume(e -> {
                    log.error("SSE 流式对话异常 conversationId={}", conversationId, e);
                    return Flux.just(STREAM_ERROR_PREFIX + "流式回复生成失败，请稍后重试");
                })
                .concatWith(Flux.just(STREAM_DONE_MARKER))
                .publish()
                .autoConnect(2);

        Flux<String> heartbeat = Flux.interval(heartbeatInterval)
                .map(tick -> HEARTBEAT_MARKER)
                .takeUntilOther(main.ignoreElements());

        return Flux.concat(Flux.just(CONVERSATION_MARKER + conversationId),
                Flux.merge(main, heartbeat));
    }

    private static String abbreviate(String message) {
        if (message.length() <= LOG_MESSAGE_MAX_LENGTH) {
            return message;
        }
        return message.substring(0, LOG_MESSAGE_MAX_LENGTH) + "...(已截断)";
    }
}
