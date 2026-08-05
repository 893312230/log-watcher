package com.smartops.infrastructure.persistence.alert;

import com.smartops.common.enums.AlertLevel;
import com.smartops.common.enums.AlertStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 告警记录 JPA 实体（表 alert_record）。
 *
 * <p>生产环境 ddl-auto=validate，表结构由 docs/sql/V1__alert_record.sql
 * 手工迁移脚本创建，本实体与脚本保持一一对应。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Getter
@Setter
@Entity
@Table(name = "alert_record", indexes = {
        @Index(name = "idx_alert_fingerprint", columnList = "fingerprint"),
        @Index(name = "idx_alert_level_created", columnList = "level,created_at")
})
public class AlertRecordEntity {

    /** 主键（自增）。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 事件指纹（L0 去重键，SHA-256 十六进制 64 字符）。 */
    @Column(nullable = false, length = 64)
    private String fingerprint;

    /** 日志来源（文件路径或 jar 包路径）。 */
    @Column(nullable = false, length = 512)
    private String source;

    /** 告警级别。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private AlertLevel level;

    /** 命中的自定义关键字（内置关键字或 null）。 */
    @Column(length = 128)
    private String keyword;

    /** 告警摘要（日志首行）。 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    /** 完整日志内容（含堆栈）。 */
    @Column(columnDefinition = "TEXT")
    private String stackTrace;

    /** 分析结论（L3/L4 产出，含降级标注）。 */
    @Column(columnDefinition = "TEXT")
    private String analysis;

    /** 解决建议。 */
    @Column(columnDefinition = "TEXT")
    private String suggestion;

    /** 到达的最高分析层级（0-4）。 */
    @Column(nullable = false)
    private int layerReached;

    /** 时间窗内合并发生次数。 */
    @Column(nullable = false)
    private int occurrence;

    /** 告警状态。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AlertStatus status;

    /** 创建时间。 */
    @Column(nullable = false)
    private Instant createdAt;

    /** 更新时间。 */
    @Column(nullable = false)
    private Instant updatedAt;
}
