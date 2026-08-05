package com.smartops.api.dto;

import com.smartops.common.enums.AuditEventType;
import com.smartops.domain.audit.AuditEvent;

import java.time.Instant;

/**
 * 审计事件视图 DTO。
 *
 * <p>REST 查询的统一输出格式，与领域模型字段一一对应。</p>
 *
 * <p>线程安全：record 不可变。</p>
 *
 * @author smartops
 * @since 1.0.0
 *
 * @param id         审计事件 id
 * @param eventType  事件类型
 * @param traceId    关联标识
 * @param actor      操作发起者
 * @param target     操作目标
 * @param detail     操作摘要
 * @param success    是否成功
 * @param latencyMs  耗时毫秒
 * @param createdAt  发生时间
 */
public record AuditEventView(
        Long id,
        AuditEventType eventType,
        String traceId,
        String actor,
        String target,
        String detail,
        boolean success,
        long latencyMs,
        Instant createdAt
) {

    /**
     * 从领域模型构造视图。
     *
     * @param event 领域审计事件
     * @return 审计事件视图
     */
    public static AuditEventView from(AuditEvent event) {
        return new AuditEventView(event.id(), event.eventType(), event.traceId(),
                event.actor(), event.target(), event.detail(), event.success(),
                event.latencyMs(), event.createdAt());
    }
}
