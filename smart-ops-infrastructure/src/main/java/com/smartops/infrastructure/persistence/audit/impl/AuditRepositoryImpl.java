package com.smartops.infrastructure.persistence.audit.impl;

import com.smartops.domain.audit.AuditEvent;
import com.smartops.domain.audit.AuditEventPage;
import com.smartops.domain.audit.AuditEventQuery;
import com.smartops.domain.audit.port.AuditRepository;
import com.smartops.infrastructure.persistence.audit.AuditEventEntity;
import com.smartops.infrastructure.persistence.audit.AuditEventJpaRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 审计持久化的 JPA 实现。
 *
 * <p>负责领域模型 {@link AuditEvent} 与 {@link AuditEventEntity} 的双向映射。
 * 写路径 {@link #save} 供异步记录器消费（不在领域查询端口内）；
 * 读路径实现领域端口 {@link AuditRepository}，列表按发生时间倒序。</p>
 *
 * <p>线程安全：Spring Data 仓库线程安全，本类无状态。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Repository
public class AuditRepositoryImpl implements AuditRepository {

    private final AuditEventJpaRepository jpaRepository;

    /**
     * 构造审计持久化实现。
     *
     * @param jpaRepository Spring Data JPA 仓库
     */
    public AuditRepositoryImpl(AuditEventJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    /**
     * 保存审计事件（供异步记录器调用）。
     *
     * @param event 审计事件
     * @return 落库后的事件（带 id）
     */
    public AuditEvent save(AuditEvent event) {
        return toDomain(jpaRepository.save(toEntity(event)));
    }

    @Override
    public AuditEventPage query(AuditEventQuery query) {
        Page<AuditEventEntity> page = jpaRepository.findAll(
                buildSpec(query),
                PageRequest.of(query.page(), query.size(),
                        Sort.by(Sort.Direction.DESC, "createdAt")));
        return new AuditEventPage(
                page.getContent().stream().map(this::toDomain).toList(),
                page.getTotalElements(),
                query.page(),
                query.size());
    }

    @Override
    public Optional<AuditEvent> findById(long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    /**
     * 按查询条件动态拼装 Specification（null 条件忽略）。
     */
    private Specification<AuditEventEntity> buildSpec(AuditEventQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.eventType() != null) {
                predicates.add(cb.equal(root.get("eventType"), query.eventType()));
            }
            if (query.traceId() != null) {
                predicates.add(cb.equal(root.get("traceId"), query.traceId()));
            }
            if (query.actor() != null && !query.actor().isBlank()) {
                predicates.add(cb.like(root.get("actor"), "%" + escapeLike(query.actor()) + "%", '\\'));
            }
            if (query.success() != null) {
                predicates.add(cb.equal(root.get("success"), query.success()));
            }
            if (query.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), query.from()));
            }
            if (query.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), query.to()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 转义 LIKE 通配符（% _ 及转义符本身），防止用户输入被解释为模式字符。
     */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /**
     * 领域模型 → 实体。
     */
    private AuditEventEntity toEntity(AuditEvent event) {
        AuditEventEntity entity = new AuditEventEntity();
        entity.setId(event.id());
        entity.setEventType(event.eventType());
        entity.setTraceId(event.traceId());
        entity.setActor(event.actor());
        entity.setTarget(event.target());
        entity.setDetail(event.detail());
        entity.setSuccess(event.success());
        entity.setLatencyMs(event.latencyMs());
        entity.setCreatedAt(event.createdAt());
        return entity;
    }

    /**
     * 实体 → 领域模型。
     */
    private AuditEvent toDomain(AuditEventEntity entity) {
        return new AuditEvent(
                entity.getId(),
                entity.getEventType(),
                entity.getTraceId(),
                entity.getActor(),
                entity.getTarget(),
                entity.getDetail(),
                entity.isSuccess(),
                entity.getLatencyMs(),
                entity.getCreatedAt());
    }
}
