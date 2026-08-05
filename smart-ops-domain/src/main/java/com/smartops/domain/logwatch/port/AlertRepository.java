package com.smartops.domain.logwatch.port;

import com.smartops.common.enums.AlertStatus;
import com.smartops.domain.logwatch.Alert;
import com.smartops.domain.logwatch.AlertPage;
import com.smartops.domain.logwatch.AlertQuery;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * 告警持久化端口。
 *
 * <p>实现位于 smart-ops-infrastructure（JPA + MySQL），
 * 消费方为 agent-core 的分析管线与 api 的查询接口。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public interface AlertRepository {

    /**
     * 保存告警（新增或更新）。
     *
     * @param alert 告警，id 为 null 时新增
     * @return 落库后的告警（带 id）
     */
    Alert save(Alert alert);

    /**
     * 按 id 查询告警。
     *
     * @param id 告警 id
     * @return 告警，不存在时为 empty
     */
    Optional<Alert> findById(long id);

    /**
     * 按条件分页查询，按创建时间倒序。
     *
     * @param query 查询条件（分页参数已归一化）
     * @return 分页结果
     */
    AlertPage query(AlertQuery query);

    /**
     * 更新告警处理状态。
     *
     * @param id     告警 id
     * @param status 新状态
     * @return 更新后的告警，不存在时为 empty
     */
    Optional<Alert> updateStatus(long id, AlertStatus status);

    /**
     * 按天统计告警数量（用于仪表盘趋势图）。
     *
     * @param since 起始时间（含）
     * @return 日期 → 告警数（仅含非零日期）
     */
    Map<LocalDate, Long> countByDay(Instant since);
}
