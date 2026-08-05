package com.smartops.infrastructure.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartops.domain.event.OpsEvent;
import com.smartops.infrastructure.persistence.webhook.WebhookDeliveryEntity;
import com.smartops.infrastructure.persistence.webhook.WebhookDeliveryJpaRepository;
import com.smartops.infrastructure.persistence.webhook.WebhookSubscriptionEntity;
import com.smartops.infrastructure.persistence.webhook.WebhookSubscriptionJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Webhook 事件投递器（阶段十二 S5）。
 *
 * <p>监听 {@link OpsEvent}，按订阅的 eventTypes 过滤后异步投递：
 * <ul>
 *   <li>报文 JSON：{type, occurredAt, data}</li>
 *   <li>订阅配置 secret 时附加 HMAC-SHA256 签名头
 *       {@code X-SmartOps-Signature: sha256=<hex>}</li>
 *   <li>失败按订阅 retryCount 重试，指数退避 1s/2s/4s...</li>
 *   <li>每次尝试落 webhook_delivery 投递日志</li>
 * </ul>
 * 线程安全：无内部状态，依赖组件线程安全。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Component
public class WebhookDispatcher {

    /** 签名请求头名。 */
    public static final String SIGNATURE_HEADER = "X-SmartOps-Signature";

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);

    private final WebhookSubscriptionJpaRepository subscriptionRepository;
    private final WebhookDeliveryJpaRepository deliveryRepository;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService retryScheduler;

    /**
     * 构造投递器（Spring 装配入口，ObjectMapper 与重试调度器自建避免依赖容器 Bean）。
     *
     * @param subscriptionRepository 订阅仓库
     * @param deliveryRepository     投递日志仓库
     */
    @org.springframework.beans.factory.annotation.Autowired
    public WebhookDispatcher(WebhookSubscriptionJpaRepository subscriptionRepository,
                             WebhookDeliveryJpaRepository deliveryRepository) {
        this(subscriptionRepository, deliveryRepository,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                new ObjectMapper(), defaultRetryScheduler());
    }

    /**
     * 构造投递器（测试用，注入 HttpClient 与重试调度器）。
     *
     * @param subscriptionRepository 订阅仓库
     * @param deliveryRepository     投递日志仓库
     * @param httpClient             HTTP 客户端
     * @param objectMapper           JSON 序列化器
     * @param retryScheduler         重试延迟调度器
     */
    public WebhookDispatcher(WebhookSubscriptionJpaRepository subscriptionRepository,
                             WebhookDeliveryJpaRepository deliveryRepository,
                             HttpClient httpClient, ObjectMapper objectMapper,
                             ScheduledExecutorService retryScheduler) {
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryRepository = deliveryRepository;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.retryScheduler = retryScheduler;
    }

    /** 默认重试调度器：单线程守护线程，JVM 退出不阻塞。 */
    private static ScheduledExecutorService defaultRetryScheduler() {
        return Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "webhook-retry");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 监听领域事件并投递到全部匹配的启用订阅。
     *
     * <p>AFTER_COMMIT：发布方在事务内（如告警确认）时，提交成功后才投递，
     * 回滚不误投递。fallbackExecution=true：告警管线 / Runbook 执行器等
     * 非事务路径（各自的落库已独立提交）立即投递，避免事件静默丢失。</p>
     *
     * @param event 领域事件
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEvent(OpsEvent event) {
        List<WebhookSubscriptionEntity> targets = subscriptionRepository.findByEnabledTrue().stream()
                .filter(s -> matches(s, event.type()))
                .toList();
        if (targets.isEmpty()) {
            return;
        }
        String body = serialize(event);
        for (WebhookSubscriptionEntity subscription : targets) {
            deliverWithRetry(subscription, event, body);
        }
    }

    /** 订阅的事件类型列表包含目标类型时匹配。 */
    private boolean matches(WebhookSubscriptionEntity subscription, String type) {
        if (subscription.getEventTypes() == null || subscription.getEventTypes().isBlank()) {
            return false;
        }
        for (String t : subscription.getEventTypes().split(",")) {
            if (t.trim().equals(type)) {
                return true;
            }
        }
        return false;
    }

    private String serialize(OpsEvent event) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "type", event.type(),
                    "occurredAt", event.occurredAt().toString(),
                    "data", event.payload()));
        } catch (Exception e) {
            return "{\"type\":\"" + event.type() + "\"}";
        }
    }

    private void deliverWithRetry(WebhookSubscriptionEntity subscription, OpsEvent event, String body) {
        deliverAttempt(subscription, event, body, 1);
    }

    /**
     * 单次尝试；失败且未达 retryCount 上限时按指数退避经调度器延迟重试
     * （第 2 次尝试前 1s、第 3 次前 2s、第 4 次前 4s），不占用工作线程 sleep。
     */
    private void deliverAttempt(WebhookSubscriptionEntity subscription, OpsEvent event,
                                String body, int attempt) {
        boolean success = deliverOnce(subscription, event, body, attempt);
        if (success) {
            return;
        }
        if (attempt > subscription.getRetryCount()) {
            log.warn("Webhook 投递最终失败 subscription={}, event={}", subscription.getName(), event.type());
            return;
        }
        long delayMs = 1000L << (attempt - 1);
        try {
            retryScheduler.schedule(() -> deliverAttempt(subscription, event, body, attempt + 1),
                    delayMs, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            log.warn("Webhook 重试调度被拒绝 subscription={}: {}", subscription.getName(), e.toString());
        }
    }

    private boolean deliverOnce(WebhookSubscriptionEntity subscription, OpsEvent event,
                                String body, int attempt) {
        Integer statusCode = null;
        boolean success = false;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(subscription.getUrl()))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (subscription.getSecret() != null && !subscription.getSecret().isBlank()) {
                builder.header(SIGNATURE_HEADER, "sha256=" + hmacSha256(subscription.getSecret(), body));
            }
            HttpResponse<String> resp = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());
            statusCode = resp.statusCode();
            success = statusCode >= 200 && statusCode < 300;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Webhook 投递异常 subscription={} attempt={}: {}",
                    subscription.getName(), attempt, e.toString());
        }
        persistDelivery(subscription, event, body, statusCode, success, attempt);
        return success;
    }

    private void persistDelivery(WebhookSubscriptionEntity subscription, OpsEvent event,
                                 String body, Integer statusCode, boolean success, int attempt) {
        try {
            WebhookDeliveryEntity delivery = new WebhookDeliveryEntity();
            delivery.setSubscriptionId(subscription.getId());
            delivery.setEventType(event.type());
            delivery.setPayload(body);
            delivery.setStatusCode(statusCode);
            delivery.setSuccess(success);
            delivery.setAttempt(attempt);
            delivery.setCreatedAt(Instant.now());
            deliveryRepository.save(delivery);
        } catch (RuntimeException e) {
            log.warn("Webhook 投递日志落库失败: {}", e.toString());
        }
    }

    /** HMAC-SHA256 签名（hex 小写）。 */
    static String hmacSha256(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 不可用", e);
        }
    }
}
