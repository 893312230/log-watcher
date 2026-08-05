package com.smartops.api.controller;

import com.smartops.api.dto.WebhookSubscriptionRequest;
import com.smartops.api.dto.WebhookSubscriptionView;
import com.smartops.infrastructure.persistence.webhook.WebhookDeliveryEntity;
import com.smartops.infrastructure.persistence.webhook.WebhookDeliveryJpaRepository;
import com.smartops.infrastructure.persistence.webhook.WebhookSubscriptionEntity;
import com.smartops.infrastructure.persistence.webhook.WebhookSubscriptionJpaRepository;
import com.smartops.infrastructure.runbook.UrlSafetyValidator;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/**
 * Webhook 订阅与投递日志 REST 入口（持久化存储）。
 *
 * <p>订阅输出经 {@link WebhookSubscriptionView} 脱敏（不含 secret）。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private final WebhookSubscriptionJpaRepository subscriptionRepository;
    private final WebhookDeliveryJpaRepository deliveryRepository;

    /**
     * 构造 Webhook 控制器。
     *
     * @param subscriptionRepository 订阅仓库
     * @param deliveryRepository     投递日志仓库
     */
    public WebhookController(WebhookSubscriptionJpaRepository subscriptionRepository,
                             WebhookDeliveryJpaRepository deliveryRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryRepository = deliveryRepository;
    }

    /**
     * 分页查询订阅（secret 脱敏）。
     *
     * @param page 页码（0 起始，可选）
     * @param size 每页大小（默认 100，上限 500，可选）
     * @return 订阅视图列表
     */
    @GetMapping
    public List<WebhookSubscriptionView> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return com.smartops.api.support.PageSlice.slice(subscriptionRepository.findAll().stream()
                        .map(WebhookSubscriptionView::from)
                        .toList(),
                page, size);
    }

    /**
     * 创建订阅（URL 经 SSRF 校验）。
     *
     * @param request 订阅请求
     * @return 含分配 id 的订阅视图
     */
    @PostMapping
    public WebhookSubscriptionView create(@Valid @RequestBody WebhookSubscriptionRequest request) {
        WebhookSubscriptionEntity entity = new WebhookSubscriptionEntity();
        applyRequest(entity, request);
        entity.setCreatedAt(Instant.now());
        return WebhookSubscriptionView.from(subscriptionRepository.save(entity));
    }

    /**
     * 更新订阅（secret 为 null 时保持原值，空串时清除）。
     *
     * @param id      订阅 id
     * @param request 订阅请求
     * @return 更新后的订阅视图
     */
    @PutMapping("/{id}")
    public WebhookSubscriptionView update(@PathVariable long id,
            @Valid @RequestBody WebhookSubscriptionRequest request) {
        WebhookSubscriptionEntity entity = subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订阅不存在: " + id));
        String previousSecret = entity.getSecret();
        applyRequest(entity, request);
        if (request.secret() == null) {
            entity.setSecret(previousSecret);
        }
        return WebhookSubscriptionView.from(subscriptionRepository.save(entity));
    }

    /**
     * 删除订阅。
     *
     * @param id 订阅 id
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) {
        subscriptionRepository.deleteById(id);
    }

    /**
     * 查询投递日志（可按订阅过滤，时间倒序，最多 100 条）。
     *
     * @param subscriptionId 订阅 id（可选）
     * @return 投递日志列表
     */
    @GetMapping("/deliveries")
    public List<WebhookDeliveryEntity> deliveries(
            @RequestParam(required = false) Long subscriptionId) {
        return subscriptionId == null
                ? deliveryRepository.findTop100ByOrderByCreatedAtDesc()
                : deliveryRepository.findTop100BySubscriptionIdOrderByCreatedAtDesc(subscriptionId);
    }

    private void applyRequest(WebhookSubscriptionEntity entity, WebhookSubscriptionRequest request) {
        entity.setName(request.name());
        entity.setUrl(UrlSafetyValidator.validate(request.url()));
        entity.setEventTypes(request.eventTypes() == null ? "" : String.join(",", request.eventTypes()));
        entity.setSecret(request.secret());
        entity.setEnabled(request.enabled() == null || request.enabled());
        entity.setRetryCount(request.retryCount() == null ? 3 : request.retryCount());
    }
}
