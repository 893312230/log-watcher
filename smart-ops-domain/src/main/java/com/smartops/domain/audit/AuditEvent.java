package com.smartops.domain.audit;

import com.smartops.common.enums.AuditEventType;

import java.time.Instant;
import java.util.Objects;

/**
 * 审计事件模型（阶段五 L2 操作审计）。
 *
 * <p>一次被审计操作的不可变记录，经 {@code AuditRecorder} 端口
 * 异步落库（不阻塞业务线程），经 {@code AuditRepository} 端口查询。</p>
 *
 * <p>线程安全：record 不可变。</p>
 *
 * @author smartops
 * @since 1.0.0
 *
 * @param id         持久化 id，未落库为 null
 * @param eventType  事件类型
 * @param traceId    关联标识（conversationId / logwatch 指纹前缀），可为 null
 * @param actor      操作发起者（如 chatService、agentRouter、工具名）
 * @param target     操作目标（模型名、工具名、执行模式等），可为 null
 * @param detail     操作摘要（入参/结果截断），可为 null
 * @param success    操作是否成功
 * @param latencyMs  操作耗时（毫秒）
 * @param createdAt  发生时间
 */
public record AuditEvent(
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

    /** detail 对外截断长度上限。 */
    public static final int DETAIL_MAX_LENGTH = 2000;

    /**
     * 紧凑构造器：必填字段非空校验，detail 截断，耗时下限归零。
     */
    public AuditEvent {
        Objects.requireNonNull(eventType, "事件类型不能为 null");
        Objects.requireNonNull(actor, "操作发起者不能为 null");
        Objects.requireNonNull(createdAt, "发生时间不能为 null");
        if (detail != null && detail.length() > DETAIL_MAX_LENGTH) {
            detail = detail.substring(0, DETAIL_MAX_LENGTH);
        }
        latencyMs = Math.max(latencyMs, 0);
    }

    /**
     * 创建审计事件（未落库，id 为 null）。
     *
     * @param eventType 事件类型
     * @param traceId   关联标识，可为 null
     * @param actor     操作发起者
     * @param target    操作目标，可为 null
     * @param detail    操作摘要，可为 null（超长自动截断）
     * @param success   是否成功
     * @param latencyMs 耗时毫秒
     * @param now       发生时间
     * @return 新审计事件
     */
    public static AuditEvent create(AuditEventType eventType, String traceId, String actor,
                                    String target, String detail, boolean success,
                                    long latencyMs, Instant now) {
        return new AuditEvent(null, eventType, traceId, actor, target, detail,
                success, latencyMs, now);
    }

    /**
     * 返回分配持久化 id 后的副本。
     *
     * @param newId 持久化 id
     * @return 带 id 的副本
     */
    public AuditEvent withId(long newId) {
        return new AuditEvent(newId, eventType, traceId, actor, target, detail,
                success, latencyMs, createdAt);
    }
}
