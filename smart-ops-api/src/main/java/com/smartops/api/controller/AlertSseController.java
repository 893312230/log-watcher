package com.smartops.api.controller;

import com.smartops.api.dto.AlertView;
import com.smartops.infrastructure.logwatch.impl.SseAlertNotifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * 告警实时推送 SSE Controller。
 *
 * <p>客户端（运维大屏/前端）通过 EventSource 订阅
 * {@code GET /api/alerts/stream}，新告警经四层分析落库后实时下发；
 * 空闲期每 15 秒推送心跳注释行防代理断连。持续流不发送
 * [DONE]（对话流式接口的终止语义在此不适用）。</p>
 *
 * <p>线程安全：Controller 单例，SseAlertNotifier 线程安全，Flux 支持背压。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/alerts")
public class AlertSseController {

    /** SSE 心跳事件（SSE 注释行格式）。 */
    public static final String HEARTBEAT_MARKER = ":heartbeat";

    /** 默认心跳间隔。 */
    private static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofSeconds(15);

    /** 告警实时通知器（SSE 多播源）。 */
    private final SseAlertNotifier notifier;

    /** 心跳间隔。 */
    private final Duration heartbeatInterval;

    /**
     * 构造 AlertSseController。
     *
     * <p>类内存在多个构造器，须以 {@link Autowired} 显式标注注入入口。</p>
     *
     * @param notifier 告警实时通知器
     */
    @Autowired
    public AlertSseController(SseAlertNotifier notifier) {
        this(notifier, DEFAULT_HEARTBEAT_INTERVAL);
    }

    /**
     * 构造 AlertSseController（指定心跳间隔，供测试使用）。
     *
     * @param notifier          告警实时通知器
     * @param heartbeatInterval 心跳间隔
     */
    AlertSseController(SseAlertNotifier notifier, Duration heartbeatInterval) {
        this.notifier = notifier;
        this.heartbeatInterval = heartbeatInterval;
    }

    /**
     * 订阅告警实时流。
     *
     * <p>每个事件为一条 {@link AlertView} JSON；心跳为 SSE 注释行。
     * 客户端断开即结束，服务端不主动终止。</p>
     *
     * @return SSE 事件流
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Object> stream() {
        Flux<Object> alerts = notifier.stream().map(AlertView::from);
        Flux<Object> heartbeat = Flux.interval(heartbeatInterval)
                .map(tick -> HEARTBEAT_MARKER);
        return Flux.merge(alerts, heartbeat);
    }
}
