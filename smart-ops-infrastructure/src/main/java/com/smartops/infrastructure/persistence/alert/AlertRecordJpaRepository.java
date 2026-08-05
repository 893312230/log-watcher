package com.smartops.infrastructure.persistence.alert;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * 告警记录 Spring Data JPA 仓库。
 *
 * <p>过滤查询走 {@link JpaSpecificationExecutor}，
 * 由 {@code AlertRepositoryImpl} 组合使用。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public interface AlertRecordJpaRepository extends JpaRepository<AlertRecordEntity, Long>,
        JpaSpecificationExecutor<AlertRecordEntity> {

    /**
     * 按天统计告警数量（CAST 兼容 MySQL 与 H2）。
     *
     * @param since 起始时间（含）
     * @return [日期(java.sql.Date), 数量(Long)] 行列表
     */
    @Query(value = "SELECT CAST(created_at AS DATE) AS d, COUNT(*) FROM alert_record "
            + "WHERE created_at >= :since GROUP BY CAST(created_at AS DATE) ORDER BY d", nativeQuery = true)
    List<Object[]> countByDaySince(@Param("since") Instant since);
}
