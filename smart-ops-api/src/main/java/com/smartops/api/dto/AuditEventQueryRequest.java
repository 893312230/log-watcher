package com.smartops.api.dto;

import com.smartops.common.enums.AuditEventType;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;

/**
 * 审计事件分页查询请求。
 *
 * <p>{@code GET /api/audit/events} 的查询参数载体（@ModelAttribute 构造绑定）；
 * 所有过滤字段可空表示不过滤，分页参数由 Controller 归一化缺省值。</p>
 *
 * <p>线程安全：record 不可变。</p>
 *
 * @author smartops
 * @since 1.0.0
 *
 * @param eventType 按事件类型过滤，null 不过滤
 * @param traceId   按关联标识过滤，null 不过滤
 * @param actor     按发起者包含匹配过滤，null 不过滤
 * @param success   按操作结果过滤，null 不过滤
 * @param from      发生时间下限（ISO-8601，含），null 不限
 * @param to        发生时间上限（ISO-8601，含），null 不限
 * @param page      页码（从 0 开始），null 视为 0
 * @param size      每页大小，null 视为默认
 */
public record AuditEventQueryRequest(
        AuditEventType eventType,
        String traceId,
        String actor,
        Boolean success,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        Integer page,
        Integer size
) {
}
