package com.smartops.infrastructure.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartops.domain.event.OpsEvent;
import com.smartops.infrastructure.persistence.webhook.WebhookDeliveryEntity;
import com.smartops.infrastructure.persistence.webhook.WebhookDeliveryJpaRepository;
import com.smartops.infrastructure.persistence.webhook.WebhookSubscriptionEntity;
import com.smartops.infrastructure.persistence.webhook.WebhookSubscriptionJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WebhookDispatcher} 单元测试（HttpClient 桩化）。
 *
 * @author smartops
 * @since 1.0.0
 */
class WebhookDispatcherTest {

    private WebhookSubscriptionJpaRepository subscriptionRepository;
    private WebhookDeliveryJpaRepository deliveryRepository;
    private HttpClient httpClient;
    private ScheduledExecutorService retryScheduler;
    private WebhookDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        subscriptionRepository = mock(WebhookSubscriptionJpaRepository.class);
        deliveryRepository = mock(WebhookDeliveryJpaRepository.class);
        httpClient = mock(HttpClient.class);
        retryScheduler = mock(ScheduledExecutorService.class);
        // 立即调度器：同步执行重试任务，便于断言
        lenient().when(retryScheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenAnswer(inv -> {
                    ((Runnable) inv.getArgument(0)).run();
                    return null;
                });
        dispatcher = new WebhookDispatcher(subscriptionRepository, deliveryRepository,
                httpClient, new ObjectMapper(), retryScheduler);
        lenient().when(deliveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private WebhookSubscriptionEntity subscription(String eventTypes, String secret, int retryCount) {
        WebhookSubscriptionEntity e = new WebhookSubscriptionEntity();
        e.setId(7L);
        e.setName("运维群");
        e.setUrl("https://example.com/hook");
        e.setEventTypes(eventTypes);
        e.setSecret(secret);
        e.setEnabled(true);
        e.setRetryCount(retryCount);
        e.setCreatedAt(Instant.now());
        return e;
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> responseWith(int status) {
        HttpResponse<String> resp = mock(HttpResponse.class);
        lenient().when(resp.statusCode()).thenReturn(status);
        return resp;
    }

    private org.mockito.stubbing.OngoingStubbing<HttpResponse<String>> whenSend() throws Exception {
        return org.mockito.Mockito.when(httpClient.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()));
    }

    @Test
    @DisplayName("事件类型匹配订阅 → 投递成功并落投递日志（含签名头）")
    void should_deliverAndPersist_when_eventTypeMatches() throws Exception {
        when(subscriptionRepository.findByEnabledTrue())
                .thenReturn(List.of(subscription("ALERT_CREATED, RUNBOOK_FAILED", "s3cret", 3)));
        HttpResponse<String> resp = responseWith(200);
        whenSend().thenReturn(resp);

        dispatcher.onEvent(OpsEvent.of(OpsEvent.ALERT_CREATED, Map.of("alertId", 1)));

        ArgumentCaptor<HttpRequest> reqCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(reqCaptor.capture(), any());
        HttpRequest sent = reqCaptor.getValue();
        assertThat(sent.uri().toString()).isEqualTo("https://example.com/hook");
        assertThat(sent.headers().firstValue(WebhookDispatcher.SIGNATURE_HEADER))
                .hasValueSatisfying(v -> assertThat(v).startsWith("sha256="));

        ArgumentCaptor<WebhookDeliveryEntity> deliveryCaptor =
                ArgumentCaptor.forClass(WebhookDeliveryEntity.class);
        verify(deliveryRepository).save(deliveryCaptor.capture());
        WebhookDeliveryEntity delivery = deliveryCaptor.getValue();
        assertThat(delivery.isSuccess()).isTrue();
        assertThat(delivery.getStatusCode()).isEqualTo(200);
        assertThat(delivery.getAttempt()).isEqualTo(1);
        assertThat(delivery.getEventType()).isEqualTo("ALERT_CREATED");
        assertThat(delivery.getPayload()).contains("ALERT_CREATED").contains("alertId");
    }

    @Test
    @DisplayName("事件类型不匹配任何订阅 → 不投递不落日志")
    void should_skip_when_eventTypeNotMatched() {
        when(subscriptionRepository.findByEnabledTrue())
                .thenReturn(List.of(subscription("RUNBOOK_FAILED", null, 3)));

        dispatcher.onEvent(OpsEvent.of(OpsEvent.ALERT_CREATED, Map.of()));

        verify(deliveryRepository, never()).save(any());
    }

    @Test
    @DisplayName("无 secret 订阅 → 不带签名头")
    void should_omitSignature_when_secretBlank() throws Exception {
        when(subscriptionRepository.findByEnabledTrue())
                .thenReturn(List.of(subscription("ALERT_ACKED", " ", 0)));
        HttpResponse<String> resp = responseWith(204);
        whenSend().thenReturn(resp);

        dispatcher.onEvent(OpsEvent.of(OpsEvent.ALERT_ACKED, Map.of("alertId", 2)));

        ArgumentCaptor<HttpRequest> reqCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(reqCaptor.capture(), any());
        assertThat(reqCaptor.getValue().headers()
                .firstValue(WebhookDispatcher.SIGNATURE_HEADER)).isEmpty();
    }

    @Test
    @DisplayName("首次 500 重试后成功 → 落两条投递日志")
    void should_retryUntilSuccess_when_firstAttemptFails() throws Exception {
        when(subscriptionRepository.findByEnabledTrue())
                .thenReturn(List.of(subscription("RUNBOOK_FAILED", null, 3)));
        HttpResponse<String> fail = responseWith(500);
        HttpResponse<String> ok = responseWith(200);
        whenSend().thenReturn(fail).thenReturn(ok);

        dispatcher.onEvent(OpsEvent.of(OpsEvent.RUNBOOK_FAILED, Map.of("runbook", "发布")));

        verify(httpClient, times(2)).send(any(), any());
        ArgumentCaptor<WebhookDeliveryEntity> captor = ArgumentCaptor.forClass(WebhookDeliveryEntity.class);
        verify(deliveryRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).isSuccess()).isFalse();
        assertThat(captor.getAllValues().get(1).isSuccess()).isTrue();
        assertThat(captor.getAllValues().get(1).getAttempt()).isEqualTo(2);
    }

    @Test
    @DisplayName("持续失败 → 按 retryCount+1 次尝试后放弃")
    void should_giveUp_when_allAttemptsFail() throws Exception {
        when(subscriptionRepository.findByEnabledTrue())
                .thenReturn(List.of(subscription("RUNBOOK_FAILED", null, 2)));
        HttpResponse<String> fail = responseWith(500);
        whenSend().thenReturn(fail);

        dispatcher.onEvent(OpsEvent.of(OpsEvent.RUNBOOK_FAILED, Map.of()));

        verify(httpClient, times(3)).send(any(), any());
        verify(deliveryRepository, times(3)).save(any());
    }

    @Test
    @DisplayName("网络异常 → 投递失败且状态码为空")
    void should_persistFailureWithoutStatus_when_networkError() throws Exception {
        when(subscriptionRepository.findByEnabledTrue())
                .thenReturn(List.of(subscription("ALERT_CREATED", null, 0)));
        whenSend().thenThrow(new java.io.IOException("连接拒绝"));

        dispatcher.onEvent(OpsEvent.of(OpsEvent.ALERT_CREATED, Map.of()));

        ArgumentCaptor<WebhookDeliveryEntity> captor = ArgumentCaptor.forClass(WebhookDeliveryEntity.class);
        verify(deliveryRepository).save(captor.capture());
        assertThat(captor.getValue().isSuccess()).isFalse();
        assertThat(captor.getValue().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("序列化失败时使用兜底报文仍完成投递")
    void should_useFallbackPayload_when_serializationFails() throws Exception {
        ObjectMapper broken = mock(ObjectMapper.class);
        when(broken.writeValueAsString(any())).thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("boom") {});
        WebhookDispatcher brokenDispatcher = new WebhookDispatcher(subscriptionRepository,
                deliveryRepository, httpClient, broken, retryScheduler);
        when(subscriptionRepository.findByEnabledTrue())
                .thenReturn(List.of(subscription("ALERT_CREATED", null, 0)));
        HttpResponse<String> resp = responseWith(200);
        whenSend().thenReturn(resp);

        brokenDispatcher.onEvent(OpsEvent.of(OpsEvent.ALERT_CREATED, Map.of()));

        ArgumentCaptor<WebhookDeliveryEntity> captor = ArgumentCaptor.forClass(WebhookDeliveryEntity.class);
        verify(deliveryRepository).save(captor.capture());
        assertThat(captor.getValue().getPayload()).contains("ALERT_CREATED");
        assertThat(captor.getValue().isSuccess()).isTrue();
    }

    @Test
    @DisplayName("投递日志落库异常不影响投递结果返回")
    void should_continue_when_deliveryPersistFails() throws Exception {
        when(subscriptionRepository.findByEnabledTrue())
                .thenReturn(List.of(subscription("ALERT_CREATED", null, 0)));
        HttpResponse<String> resp = responseWith(200);
        whenSend().thenReturn(resp);
        when(deliveryRepository.save(any())).thenThrow(new RuntimeException("DB 不可用"));

        dispatcher.onEvent(OpsEvent.of(OpsEvent.ALERT_CREATED, Map.of()));

        verify(httpClient).send(any(), any());
    }

    @Test
    @DisplayName("投递线程被中断 → 本次失败且重试循环正常结束")
    void should_handleInterrupt_when_sendInterrupted() throws Exception {
        when(subscriptionRepository.findByEnabledTrue())
                .thenReturn(List.of(subscription("ALERT_CREATED", null, 1)));
        whenSend().thenThrow(new InterruptedException("中断"));

        dispatcher.onEvent(OpsEvent.of(OpsEvent.ALERT_CREATED, Map.of()));

        ArgumentCaptor<WebhookDeliveryEntity> captor = ArgumentCaptor.forClass(WebhookDeliveryEntity.class);
        verify(deliveryRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allMatch(d -> !d.isSuccess());
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    @DisplayName("secret 为 null 的订阅投递不带签名头")
    void should_omitSignature_when_secretNull() throws Exception {
        when(subscriptionRepository.findByEnabledTrue())
                .thenReturn(List.of(subscription("ALERT_CREATED", null, 0)));
        HttpResponse<String> resp = responseWith(200);
        whenSend().thenReturn(resp);

        dispatcher.onEvent(OpsEvent.of(OpsEvent.ALERT_CREATED, Map.of()));

        ArgumentCaptor<HttpRequest> reqCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(reqCaptor.capture(), any());
        assertThat(reqCaptor.getValue().headers()
                .firstValue(WebhookDispatcher.SIGNATURE_HEADER)).isEmpty();
    }

    @Test
    @DisplayName("订阅事件类型为 null 或空白时不投递")
    void should_skip_when_eventTypesNullOrBlank() {
        when(subscriptionRepository.findByEnabledTrue())
                .thenReturn(List.of(subscription(null, null, 3), subscription(" ", null, 3)));

        dispatcher.onEvent(OpsEvent.of(OpsEvent.ALERT_CREATED, Map.of()));

        verify(deliveryRepository, never()).save(any());
    }

    @Test
    @DisplayName("首次失败后按 1s 延迟调度第二次尝试（不阻塞工作线程）")
    void should_scheduleRetryWithBackoff_when_firstAttemptFails() throws Exception {
        when(subscriptionRepository.findByEnabledTrue())
                .thenReturn(List.of(subscription("RUNBOOK_FAILED", null, 3)));
        HttpResponse<String> fail = responseWith(500);
        HttpResponse<String> ok = responseWith(200);
        whenSend().thenReturn(fail).thenReturn(ok);

        dispatcher.onEvent(OpsEvent.of(OpsEvent.RUNBOOK_FAILED, Map.of()));

        verify(retryScheduler).schedule(any(Runnable.class), eq(1000L), eq(TimeUnit.MILLISECONDS));
        verify(httpClient, times(2)).send(any(), any());
    }

    @Test
    @DisplayName("HMAC-SHA256 签名值与参考实现一致")
    void should_computeKnownHmac() {
        // 参考值：printf 'body' | openssl dgst -sha256 -hmac 'key'
        assertThat(WebhookDispatcher.hmacSha256("key", "body"))
                .isEqualTo("515aae133b435d4000956731f68ae5cf5eb85d4f0dc6a546d2bfcd3595ec1ae1");
    }
}
