package com.smartops.api.controller;

import com.smartops.infrastructure.persistence.webhook.WebhookDeliveryEntity;
import com.smartops.infrastructure.persistence.webhook.WebhookDeliveryJpaRepository;
import com.smartops.infrastructure.persistence.webhook.WebhookSubscriptionEntity;
import com.smartops.infrastructure.persistence.webhook.WebhookSubscriptionJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WebhookController} Web 层测试。
 *
 * @author smartops
 * @since 1.0.0
 */
@WebMvcTest(WebhookController.class)
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WebhookSubscriptionJpaRepository subscriptionRepository;

    @MockitoBean
    private WebhookDeliveryJpaRepository deliveryRepository;

    private WebhookSubscriptionEntity subscription(long id, String name) {
        WebhookSubscriptionEntity e = new WebhookSubscriptionEntity();
        e.setId(id);
        e.setName(name);
        e.setUrl("https://example.com/hook");
        e.setEventTypes("ALERT_CREATED,RUNBOOK_FAILED");
        e.setSecret("s3cret");
        e.setEnabled(true);
        e.setRetryCount(3);
        e.setCreatedAt(Instant.now());
        return e;
    }

    @Test
    @DisplayName("list 返回全部订阅且不含 secret（脱敏）")
    void should_listSubscriptions() throws Exception {
        when(subscriptionRepository.findAll()).thenReturn(List.of(subscription(1L, "运维群")));
        mockMvc.perform(get("/api/webhooks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("运维群"))
                .andExpect(jsonPath("$[0].eventTypes").value("ALERT_CREATED,RUNBOOK_FAILED"))
                .andExpect(jsonPath("$[0].hasSecret").value(true))
                .andExpect(jsonPath("$[0].secret").doesNotExist());
    }

    @Test
    @DisplayName("create 创建订阅并返回分配 id（响应不含 secret）")
    void should_createSubscription() throws Exception {
        when(subscriptionRepository.save(any())).thenReturn(subscription(7L, "告警群"));
        mockMvc.perform(post("/api/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"告警群\",\"url\":\"https://example.com/hook\","
                                + "\"eventTypes\":[\"ALERT_CREATED\"],\"retryCount\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.secret").doesNotExist());
    }

    @Test
    @DisplayName("create 缺少必填字段返回 400（@Valid）")
    void should_return400_when_requiredFieldMissing() throws Exception {
        mockMvc.perform(post("/api/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"url\":\"https://example.com/hook\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("create 内网 URL 被 SSRF 校验拒绝返回 400")
    void should_return400_when_urlInternal() throws Exception {
        mockMvc.perform(post("/api/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"url\":\"http://192.168.1.1/hook\",\"eventTypes\":[\"A\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("update 更新已存在订阅")
    void should_updateSubscription() throws Exception {
        WebhookSubscriptionEntity existing = subscription(3L, "旧名");
        when(subscriptionRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/webhooks/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新名\",\"url\":\"https://example.com/h2\","
                                + "\"eventTypes\":[\"RUNBOOK_FAILED\"],\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("新名"))
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    @DisplayName("update 未传 secret 时保持原值")
    void should_keepSecret_when_updateOmitsIt() throws Exception {
        WebhookSubscriptionEntity existing = subscription(3L, "旧名");
        when(subscriptionRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/webhooks/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新名\",\"url\":\"https://example.com/h2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasSecret").value(true));
        org.assertj.core.api.Assertions.assertThat(existing.getSecret()).isEqualTo("s3cret");
    }

    @Test
    @DisplayName("update 订阅不存在返回 404")
    void should_return404_when_updateMissing() throws Exception {
        when(subscriptionRepository.findById(99L)).thenReturn(Optional.empty());
        mockMvc.perform(put("/api/webhooks/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"url\":\"https://example.com/h\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("delete 删除订阅返回 200")
    void should_deleteSubscription() throws Exception {
        mockMvc.perform(delete("/api/webhooks/3"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("deliveries 无过滤参数返回全部最近投递")
    void should_listAllDeliveries() throws Exception {
        WebhookDeliveryEntity d = new WebhookDeliveryEntity();
        d.setId(1L);
        d.setSubscriptionId(7L);
        d.setEventType("ALERT_CREATED");
        d.setPayload("{}");
        d.setStatusCode(200);
        d.setSuccess(true);
        d.setAttempt(1);
        d.setCreatedAt(Instant.now());
        when(deliveryRepository.findTop100ByOrderByCreatedAtDesc()).thenReturn(List.of(d));

        mockMvc.perform(get("/api/webhooks/deliveries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("ALERT_CREATED"))
                .andExpect(jsonPath("$[0].success").value(true));
    }

    @Test
    @DisplayName("deliveries 按订阅过滤")
    void should_listDeliveriesBySubscription() throws Exception {
        when(deliveryRepository.findTop100BySubscriptionIdOrderByCreatedAtDesc(7L))
                .thenReturn(List.of());
        mockMvc.perform(get("/api/webhooks/deliveries").param("subscriptionId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
