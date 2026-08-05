package com.smartops.domain.event;

import java.time.Instant;
import java.util.Map;

/**
 * 运维平台领域事件（阶段十二 Webhook 事件总线）。
 *
 * <p>通过 Spring {@code ApplicationEventPublisher} 在 JVM 内发布，
 * 由 WebhookDispatcher 监听并投递到订阅方。事件类型见常量定义。</p>
 *
 * @param type       事件类型（如 {@link #ALERT_CREATED}）
 * @param payload    事件数据（键值对，序列化为 JSON 投递）
 * @param occurredAt 事件发生时间
 * @author smartops
 * @since 1.0.0
 */
public record OpsEvent(String type, Map<String, Object> payload, Instant occurredAt) {

    /** 告警创建。 */
    public static final String ALERT_CREATED = "ALERT_CREATED";

    /** 告警确认。 */
    public static final String ALERT_ACKED = "ALERT_ACKED";

    /** Runbook 执行失败。 */
    public static final String RUNBOOK_FAILED = "RUNBOOK_FAILED";

    /** Runbook 执行成功。 */
    public static final String RUNBOOK_COMPLETED = "RUNBOOK_COMPLETED";

    /** 事件复盘报告生成。 */
    public static final String INCIDENT_POSTMORTEM = "INCIDENT_POSTMORTEM";

    /**
     * 创建事件（发生时间取当前时刻）。
     *
     * @param type    事件类型
     * @param payload 事件数据
     * @return 领域事件
     */
    public static OpsEvent of(String type, Map<String, Object> payload) {
        return new OpsEvent(type, payload, Instant.now());
    }
}
