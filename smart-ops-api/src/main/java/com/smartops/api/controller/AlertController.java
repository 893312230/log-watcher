package com.smartops.api.controller;

import com.smartops.api.dto.AlertPageView;
import com.smartops.api.dto.AlertQueryRequest;
import com.smartops.api.dto.AlertView;
import com.smartops.common.enums.AlertStatus;
import com.smartops.domain.knowledge.KnowledgeEntry;
import com.smartops.domain.knowledge.port.KnowledgeRepository;
import com.smartops.domain.logwatch.AlertPage;
import com.smartops.domain.logwatch.AlertQuery;
import com.smartops.domain.logwatch.port.AlertRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 告警查询与处理 REST Controller。
 *
 * <p>提供告警分页查询（级别/来源/时间范围过滤，创建时间倒序）、
 * 单条详情与确认（ack）操作；实时推送见 {@link AlertSseController}。</p>
 *
 * <p>线程安全：Controller 单例，依赖的 AlertRepository 线程安全。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    /** 默认每页大小。 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 告警持久化端口。 */
    private final AlertRepository repository;

    /** 知识库持久化端口。 */
    private final KnowledgeRepository knowledgeRepository;

    /** 领域事件发布器（Webhook 订阅投递）。 */
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    /**
     * 构造 AlertController。
     *
     * @param repository          告警持久化端口
     * @param knowledgeRepository 知识库持久化端口
     * @param eventPublisher      领域事件发布器
     */
    public AlertController(AlertRepository repository, KnowledgeRepository knowledgeRepository,
                           org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.knowledgeRepository = knowledgeRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 分页查询告警（创建时间倒序）。
     *
     * @param request 查询条件（级别/来源/时间范围/分页）
     * @return 分页结果
     */
    @GetMapping
    public AlertPageView list(AlertQueryRequest request) {
        AlertQuery query = new AlertQuery(
                request.level(),
                request.source(),
                request.keyword(),
                request.from(),
                request.to(),
                request.page() == null ? 0 : request.page(),
                request.size() == null ? DEFAULT_PAGE_SIZE : request.size());
        AlertPage page = repository.query(query);
        return AlertPageView.from(page);
    }

    /**
     * 按天统计告警数量（缺失日期补零，用于仪表盘趋势图）。
     *
     * @param days 统计天数（1-90，默认 7）
     * @return [{date, count}] 按日期升序
     */
    @GetMapping("/stats/daily")
    public List<Map<String, Object>> dailyStats(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "7") int days) {
        int bounded = Math.min(Math.max(days, 1), 90);
        LocalDate today = LocalDate.now();
        Map<LocalDate, Long> counts = repository.countByDay(
                Instant.now().minus(java.time.Duration.ofDays(bounded)));
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = bounded - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            result.add(Map.of("date", day.toString(), "count", counts.getOrDefault(day, 0L)));
        }
        return result;
    }

    /**
     * 查询单条告警详情。
     *
     * @param id 告警 id
     * @return 告警视图
     */
    @GetMapping("/{id}")
    public AlertView detail(@PathVariable long id) {
        return repository.findById(id)
                .map(AlertView::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "告警不存在: " + id));
    }

    /**
     * 确认告警（状态置为 ACKED）。
     *
     * <p>事务边界：状态更新与 ALERT_ACKED 事件发布同事务，
     * 事件监听（Webhook 投递）在事务提交后触发，回滚不误投递。</p>
     *
     * @param id 告警 id
     * @return 更新后的告警视图
     */
    @PostMapping("/{id}/ack")
    @Transactional
    public AlertView ack(@PathVariable long id) {
        AlertView view = repository.updateStatus(id, AlertStatus.ACKED)
                .map(AlertView::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "告警不存在: " + id));
        eventPublisher.publishEvent(com.smartops.domain.event.OpsEvent.of(
                com.smartops.domain.event.OpsEvent.ALERT_ACKED,
                java.util.Map.of("alertId", id)));
        return view;
    }

    /**
     * 告警转知识库条目。
     */
    @PostMapping("/{id}/to-knowledge")
    public KnowledgeEntry toKnowledge(@PathVariable long id) {
        var alert = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        KnowledgeEntry entry = KnowledgeEntry.create(
                alert.keyword() != null ? alert.keyword() + " — " + alert.message() : alert.message(),
                alert.keyword(),
                alert.analysis(),
                alert.suggestion(),
                java.util.List.of(),
                alert.level().name(),
                java.util.List.of(),
                "MANUAL",
                id,
                null,
                com.smartops.api.auth.CurrentActor.username(),
                java.time.Instant.now());
        return knowledgeRepository.save(entry);
    }
}
