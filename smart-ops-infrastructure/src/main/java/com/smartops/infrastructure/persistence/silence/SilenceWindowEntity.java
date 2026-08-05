package com.smartops.infrastructure.persistence.silence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 告警静默窗口 JPA 实体（表 silence_window）。
 *
 * @author smartops
 * @since 1.0.0
 */
@Getter
@Setter
@Entity
@Table(name = "silence_window")
public class SilenceWindowEntity {

    /** 主键（自增）。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 日志来源匹配串（包含匹配，空表示全部）。 */
    @Column(length = 512)
    private String sourceMatcher;

    /** 告警级别过滤（空表示全部）。 */
    @Column(length = 16)
    private String levelFilter;

    /** 窗口开始时间。 */
    @Column(nullable = false)
    private Instant startAt;

    /** 窗口结束时间。 */
    @Column(nullable = false)
    private Instant endAt;

    /** 静默原因。 */
    @Column(length = 512)
    private String reason;

    /** 创建人。 */
    @Column(length = 64)
    private String createdBy;

    /** 创建时间。 */
    @Column(nullable = false)
    private Instant createdAt;
}
