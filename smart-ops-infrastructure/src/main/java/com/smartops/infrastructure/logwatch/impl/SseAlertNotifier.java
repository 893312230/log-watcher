package com.smartops.infrastructure.logwatch.impl;

import com.smartops.domain.logwatch.Alert;
import com.smartops.domain.logwatch.port.AlertNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * SSE 实时告警通知器。
 *
 * <p>{@link AlertNotifier} 端口的 Reactor 实现：基于
 * {@link Sinks.Many} 多播背压缓冲，向所有在线订阅者广播告警。
 * 按端口契约尽力投递：无订阅者或发射失败仅记日志，
 * 绝不阻塞分析线程（告警已先落库，实时推送只是增值通道）。</p>
 *
 * <p>常驻组件（不随 smartops.logwatch.enabled 条件化）：logwatch 关闭时
 * SSE 端点仍可注入，流上仅剩心跳。</p>
 *
 * <p>线程安全：Sinks 内部线程安全，可多线程 publish。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Component
public class SseAlertNotifier implements AlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(SseAlertNotifier.class);

    private final Sinks.Many<Alert> sink;

    /**
     * 构造 SSE 告警通知器。
     *
     * @param bufferSize 背压缓冲容量（慢订阅者积压上界，超出丢弃）
     */
    public SseAlertNotifier(
            @Value("${smartops.logwatch.sse.buffer-size:256}") int bufferSize) {
        this.sink = Sinks.many().multicast().onBackpressureBuffer(bufferSize, false);
    }

    @Override
    public void publish(Alert alert) {
        Sinks.EmitResult result = sink.tryEmitNext(alert);
        if (result.isFailure()) {
            log.debug("告警实时推送未投递（无订阅者或缓冲满）: {}", result);
        }
    }

    /**
     * 告警实时流（供 API 层 SSE 端点订阅）。
     *
     * @return 持续不完成的告警 Flux
     */
    public Flux<Alert> stream() {
        return sink.asFlux();
    }
}
