package com.smartops.domain.audit.port;

import com.smartops.domain.audit.AuditEvent;
import com.smartops.domain.audit.AuditEventPage;
import com.smartops.domain.audit.AuditEventQuery;

import java.util.Optional;

/**
 * 审计查询端口（读路径）。
 *
 * <p>实现位于 smart-ops-infrastructure（JPA + MySQL），
 * 消费方为 api 的审计查询接口。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public interface AuditRepository {

    /**
     * 按条件分页查询，按发生时间倒序。
     *
     * @param query 查询条件（分页参数已归一化）
     * @return 分页结果
     */
    AuditEventPage query(AuditEventQuery query);

    /**
     * 按 id 查询单条审计事件。
     *
     * @param id 事件 id
     * @return 审计事件，不存在时为 empty
     */
    Optional<AuditEvent> findById(long id);
}
