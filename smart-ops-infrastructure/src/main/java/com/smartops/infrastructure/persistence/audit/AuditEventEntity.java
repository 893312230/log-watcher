package com.smartops.infrastructure.persistence.audit;

import com.smartops.common.enums.AuditEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 审计事件 JPA 实体（表 audit_event）。
 *
 * <p>生产环境 ddl-auto=validate，表结构由 docs/sql/V2__audit_event.sql
 * 手工迁移脚本创建，本实体与脚本保持一一对应。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Getter
@Setter
@Entity
@Table(name = "audit_event", indexes = {
        @Index(name = "idx_audit_type_created", columnList = "event_type,created_at"),
        @Index(name = "idx_audit_trace", columnList = "trace_id")
})
public class AuditEventEntity {

    /** 主键（自增）。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 事件类型。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AuditEventType eventType;

    /** 关联标识（conversationId / logwatch 指纹前缀）。 */
    @Column(length = 128)
    private String traceId;

    /** 操作发起者。 */
    @Column(nullable = false, length = 128)
    private String actor;

    /** 操作目标（模型名/工具名/执行模式）。 */
    @Column(length = 255)
    private String target;

    /** 操作摘要（入参/结果截断）。 */
    @Column(columnDefinition = "TEXT")
    private String detail;

    /** 操作是否成功。 */
    @Column(nullable = false)
    private boolean success;

    /** 操作耗时（毫秒）。 */
    @Column(nullable = false)
    private long latencyMs;

    /** 发生时间。 */
    @Column(nullable = false)
    private Instant createdAt;
}
