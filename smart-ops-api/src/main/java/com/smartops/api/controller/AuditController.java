package com.smartops.api.controller;

import com.smartops.api.dto.AuditEventPageView;
import com.smartops.api.dto.AuditEventQueryRequest;
import com.smartops.api.dto.AuditEventView;
import com.smartops.domain.audit.AuditEventQuery;
import com.smartops.domain.audit.port.AuditRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * 审计事件查询 REST Controller（阶段五 L2 操作审计）。
 *
 * <p>提供审计事件分页查询（事件类型/关联标识/时间范围过滤，发生时间倒序）。
 * 审计事件由 Observability 钩子经异步记录器落库，本端点只读。</p>
 *
 * <p>线程安全：Controller 单例，依赖的 AuditRepository 线程安全。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/audit/events")
public class AuditController {

    /** 默认每页大小。 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 审计事件查询端口。 */
    private final AuditRepository repository;

    /**
     * 构造 AuditController。
     *
     * @param repository 审计事件查询端口
     */
    public AuditController(AuditRepository repository) {
        this.repository = repository;
    }

    /**
     * 分页查询审计事件（发生时间倒序）。
     *
     * @param request 查询条件（事件类型/关联标识/时间范围/分页）
     * @return 分页结果
     */
    @GetMapping
    public AuditEventPageView list(AuditEventQueryRequest request) {
        AuditEventQuery query = new AuditEventQuery(
                request.eventType(),
                request.traceId(),
                request.actor(),
                request.success(),
                request.from(),
                request.to(),
                request.page() == null ? 0 : request.page(),
                request.size() == null ? DEFAULT_PAGE_SIZE : request.size());
        return AuditEventPageView.from(repository.query(query));
    }

    /** CSV 导出审计事件（全字段转义 + 公式注入防护）。 */
    @GetMapping("/export")
    public void export(AuditEventQueryRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/csv;charset=UTF-8");
        resp.setHeader("Content-Disposition", "attachment;filename=audit_export.csv");
        PrintWriter w = resp.getWriter();
        w.write("﻿"); // BOM for Excel
        w.println("id,eventType,traceId,actor,target,detail,success,latencyMs,createdAt");
        var query = new AuditEventQuery(req.eventType(), req.traceId(), req.actor(), req.success(),
                req.from(), req.to(), 0, 1000);
        for (var e : repository.query(query).items()) {
            w.printf("%d,%s,%s,%s,%s,%s,%s,%d,%s\n",
                    e.id(), csv(e.eventType() == null ? null : e.eventType().name()), csv(e.traceId()),
                    csv(e.actor()), csv(e.target()), csv(e.detail()),
                    e.success(), e.latencyMs(), e.createdAt());
        }
    }

    /**
     * CSV 字段转义：null 转空串，统一加引号并双写内部引号；
     * 前导 {@code = + - @} 加 {@code '} 前缀防 Excel/Sheets 公式注入。
     */
    static String csv(String value) {
        if (value == null) {
            return "\"\"";
        }
        String v = value;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    /**
     * 查询单条审计事件详情。
     *
     * @param id 事件 id
     * @return 审计事件视图
     */
    @GetMapping("/{id}")
    public AuditEventView detail(@org.springframework.web.bind.annotation.PathVariable long id) {
        return repository.findById(id)
                .map(AuditEventView::from)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "审计事件不存在: " + id));
    }
}
