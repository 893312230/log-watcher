package com.smartops.infrastructure.notification;

import com.smartops.domain.logwatch.Alert;
import com.smartops.infrastructure.persistence.notification.NotificationChannelEntity;
import com.smartops.infrastructure.persistence.notification.NotificationChannelJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 多渠道通知服务（阶段七，阶段十二持久化）。
 *
 * <p>渠道从数据库读取（每次通知取最新启用列表），告警完成时异步推送 JSON 消息。
 * 线程安全：本类无状态，仓库线程安全。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationChannelJpaRepository channelRepository;
    private final HttpClient http;

    /**
     * 构造通知服务。
     *
     * @param channelRepository 通知渠道仓库
     */
    @org.springframework.beans.factory.annotation.Autowired
    public NotificationService(NotificationChannelJpaRepository channelRepository) {
        this(channelRepository,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    /**
     * 构造通知服务（测试用，注入 HttpClient）。
     *
     * @param channelRepository 通知渠道仓库
     * @param http              HTTP 客户端
     */
    public NotificationService(NotificationChannelJpaRepository channelRepository, HttpClient http) {
        this.channelRepository = channelRepository;
        this.http = http;
    }

    /**
     * 异步发送告警通知到全部启用渠道。
     *
     * @param alert 告警
     */
    public void notify(Alert alert) {
        for (NotificationChannelEntity ch : channelRepository.findByEnabledTrue()) {
            CompletableFuture.runAsync(() -> send(ch, alert));
        }
    }

    private void send(NotificationChannelEntity channel, Alert alert) {
        try {
            Map<String, Object> payload = Map.of(
                    "text", String.format(
                            "[%s] %s\n来源: %s\n分析层: L%d\n%s",
                            alert.level(), alert.message(),
                            alert.source(), alert.layerReached(),
                            alert.analysis() != null
                                    ? alert.analysis().substring(0, Math.min(alert.analysis().length(), 200))
                                    : ""),
                    "alertId", alert.id() == null ? 0 : alert.id(),
                    "level", alert.level().name(),
                    "source", alert.source());
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(channel.getTargetUrl()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(10))
                    .build();
            http.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            log.warn("通知发送失败 channel={}: {}", channel.getName(), e.toString());
        }
    }
}
