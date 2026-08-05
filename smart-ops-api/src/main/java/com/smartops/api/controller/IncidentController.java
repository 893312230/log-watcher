package com.smartops.api.controller;

import com.smartops.infrastructure.chat.ChatService;
import com.smartops.domain.logwatch.port.AlertRepository;
import com.smartops.domain.logwatch.Alert;
import com.smartops.domain.logwatch.AlertQuery;
import com.smartops.domain.knowledge.KnowledgeEntry;
import com.smartops.domain.knowledge.port.KnowledgeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

/**
 * 事件（Incident）REST Controller。
 *
 * <p>事件由最近告警按「来源 + 5 分钟窗口」聚合而成，
 * 事件 id 取组内首条（最新）告警的持久化 id——跨请求稳定，
 * postmortem 据此定位事件组，查不到返回 404。</p>
 *
 * <p>线程安全：Controller 单例，依赖组件线程安全。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    /** 事件聚合读取的最近告警条数上限。 */
    private static final int GROUP_WINDOW_LIMIT = 100;

    /** 事件分组秒数（5 分钟窗口）。 */
    private static final long GROUP_WINDOW_SECONDS = 300;

    private final AlertRepository alertRepo;
    private final KnowledgeRepository knowledgeRepo;
    private final ChatService chatService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public IncidentController(AlertRepository alertRepo, KnowledgeRepository knowledgeRepo,
                               ChatService chatService,
                               org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.alertRepo = alertRepo;
        this.knowledgeRepo = knowledgeRepo;
        this.chatService = chatService;
        this.eventPublisher = eventPublisher;
    }

    /** 按时间窗口分组告警为事件。 */
    @GetMapping
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> incidents = new ArrayList<>();
        for (IncidentGroup group : groupRecentIncidents()) {
            var alerts = group.alerts();
            incidents.add(Map.of(
                    "id", group.id(),
                    "source", alerts.get(0).source(),
                    "alertCount", alerts.size(),
                    "firstAt", alerts.get(alerts.size() - 1).createdAt(),
                    "lastAt", alerts.get(0).createdAt(),
                    "level", alerts.get(0).level().name(),
                    "alerts", alerts));
        }
        return incidents;
    }

    /**
     * 生成事后复盘报告：按 path id 定位事件组，仅用该组告警构建上下文。
     *
     * @param id   事件 id（组内首条告警 id）
     * @param body 可选自定义上下文
     * @return 落库的复盘知识条目
     */
    @PostMapping("/{id}/postmortem")
    @Transactional
    public KnowledgeEntry postmortem(@PathVariable long id, @RequestBody(required = false) Map<String, Object> body) {
        IncidentGroup group = groupRecentIncidents().stream()
                .filter(g -> Objects.equals(g.id(), id))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "事件不存在: " + id));
        StringBuilder ctx = new StringBuilder();
        for (var alert : group.alerts()) {
            ctx.append("[").append(alert.level()).append("] ")
               .append(alert.message()).append("\n");
            if (alert.analysis() != null)
                ctx.append("分析: ").append(alert.analysis()).append("\n");
        }
        String userContext = body != null && body.containsKey("context")
                ? (String) body.get("context") : ctx.toString();
        if (userContext.isBlank()) userContext = ctx.toString();
        String report = chatService.chatWithSystemPrompt(
                "你是一个事后复盘专家。根据以下事件信息生成结构化复盘报告：" +
                "【事件概述】【时间线】【根因分析】【影响范围】【修复措施】【预防建议】",
                userContext);
        KnowledgeEntry saved = knowledgeRepo.save(KnowledgeEntry.create(
                "事后复盘 #" + id, null, report, null, List.of(),
                "POSTMORTEM", List.of(), "POSTMORTEM", null, null,
                com.smartops.api.auth.CurrentActor.username(), Instant.now()));
        eventPublisher.publishEvent(com.smartops.domain.event.OpsEvent.of(
                com.smartops.domain.event.OpsEvent.INCIDENT_POSTMORTEM,
                Map.of("incidentId", id, "entryId", saved.id() == null ? 0 : saved.id())));
        return saved;
    }

    /** 事件分组：最近告警按 来源+5 分钟窗口 聚合，组 id = 组内首条告警的持久化 id。 */
    private List<IncidentGroup> groupRecentIncidents() {
        var page = alertRepo.query(new AlertQuery(null, null, null, null, null, 0, GROUP_WINDOW_LIMIT));
        Map<String, List<Alert>> groups = new LinkedHashMap<>();
        for (Alert a : page.items()) {
            String key = a.source() + "|" + (a.createdAt().getEpochSecond() / GROUP_WINDOW_SECONDS);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(a);
        }
        List<IncidentGroup> result = new ArrayList<>();
        for (var alerts : groups.values()) {
            result.add(new IncidentGroup(alerts.get(0).id(), alerts));
        }
        return result;
    }

    /** 事件分组视图：id 为组内首条告警 id。 */
    private record IncidentGroup(Long id, List<Alert> alerts) {}
}
