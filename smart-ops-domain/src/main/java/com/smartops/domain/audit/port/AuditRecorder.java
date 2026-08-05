package com.smartops.domain.audit.port;

import com.smartops.domain.audit.AuditEvent;

/**
 * 审计记录端口（写路径）。
 *
 * <p>实现位于 smart-ops-infrastructure（异步有界队列 + MySQL）。
 * 契约：尽力投递——任何情况下不得抛出异常、不得阻塞调用方业务线程，
 * 队列满时丢弃并计数（审计丢失不得影响业务主链路）。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public interface AuditRecorder {

    /**
     * 记录一条审计事件（异步、尽力投递）。
     *
     * @param event 审计事件
     */
    void record(AuditEvent event);
}
