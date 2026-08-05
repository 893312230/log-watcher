package com.smartops.infrastructure.persistence.alert.impl;

import com.smartops.common.enums.AlertStatus;
import com.smartops.common.exception.LogWatchException;
import com.smartops.domain.logwatch.Alert;
import com.smartops.domain.logwatch.AlertPage;
import com.smartops.domain.logwatch.AlertQuery;
import com.smartops.domain.logwatch.port.AlertRepository;
import com.smartops.infrastructure.persistence.alert.AlertRecordEntity;
import com.smartops.infrastructure.persistence.alert.AlertRecordJpaRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 告警持久化端口的 JPA 实现。
 *
 * <p>负责领域模型 {@link Alert} 与 {@link AlertRecordEntity} 的双向映射，
 * 过滤条件动态拼装（Specification），列表按创建时间倒序。</p>
 *
 * <p>线程安全：Spring Data 仓库线程安全，本类无状态。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Repository
public class AlertRepositoryImpl implements AlertRepository {

    private final AlertRecordJpaRepository jpaRepository;

    /**
     * 构造告警持久化实现。
     *
     * @param jpaRepository Spring Data JPA 仓库
     */
    public AlertRepositoryImpl(AlertRecordJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Alert save(Alert alert) {
        return toDomain(jpaRepository.save(toEntity(alert)));
    }

    @Override
    public Optional<Alert> findById(long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public AlertPage query(AlertQuery query) {
        Page<AlertRecordEntity> page = jpaRepository.findAll(
                buildSpec(query),
                PageRequest.of(query.page(), query.size(),
                        Sort.by(Sort.Direction.DESC, "createdAt")));
        return new AlertPage(
                page.getContent().stream().map(this::toDomain).toList(),
                page.getTotalElements(),
                query.page(),
                query.size());
    }

    @Override
    public Optional<Alert> updateStatus(long id, AlertStatus status) {
        return jpaRepository.findById(id)
                .map(entity -> {
                    entity.setStatus(status);
                    entity.setUpdatedAt(java.time.Instant.now());
                    return toDomain(jpaRepository.save(entity));
                });
    }

    @Override
    public Map<LocalDate, Long> countByDay(Instant since) {
        Map<LocalDate, Long> result = new java.util.LinkedHashMap<>();
        for (Object[] row : jpaRepository.countByDaySince(since)) {
            Object raw = row[0];
            LocalDate day;
            if (raw instanceof java.sql.Date d) {
                day = d.toLocalDate();
            } else if (raw instanceof LocalDate d) {
                day = d;
            } else if (raw instanceof java.sql.Timestamp t) {
                day = t.toLocalDateTime().toLocalDate();
            } else {
                day = LocalDate.parse(raw.toString());
            }
            result.put(day, ((Number) row[1]).longValue());
        }
        return result;
    }

    /**
     * 按查询条件动态拼装 Specification（null 条件忽略）。
     */
    private Specification<AlertRecordEntity> buildSpec(AlertQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.level() != null) {
                predicates.add(cb.equal(root.get("level"), query.level()));
            }
            if (query.source() != null && !query.source().isBlank()) {
                predicates.add(cb.like(root.get("source"), "%" + escapeLike(query.source()) + "%", '\\'));
            }
            if (query.keyword() != null && !query.keyword().isBlank()) {
                predicates.add(cb.like(root.get("keyword"), "%" + escapeLike(query.keyword()) + "%", '\\'));
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
    private AlertRecordEntity toEntity(Alert alert) {
        AlertRecordEntity entity = new AlertRecordEntity();
        entity.setId(alert.id());
        entity.setFingerprint(alert.fingerprint());
        entity.setSource(alert.source());
        entity.setLevel(alert.level());
        entity.setKeyword(alert.keyword());
        entity.setMessage(alert.message());
        entity.setStackTrace(alert.stackTrace());
        entity.setAnalysis(alert.analysis());
        entity.setSuggestion(alert.suggestion());
        entity.setLayerReached(alert.layerReached());
        entity.setOccurrence(alert.occurrence());
        entity.setStatus(alert.status());
        entity.setCreatedAt(alert.createdAt());
        entity.setUpdatedAt(alert.updatedAt());
        return entity;
    }

    /**
     * 实体 → 领域模型。
     */
    private Alert toDomain(AlertRecordEntity entity) {
        return new Alert(
                entity.getId(),
                entity.getFingerprint(),
                entity.getSource(),
                entity.getLevel(),
                entity.getKeyword(),
                entity.getMessage(),
                entity.getStackTrace(),
                entity.getAnalysis(),
                entity.getSuggestion(),
                entity.getLayerReached(),
                entity.getOccurrence(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
