package com.smartops.infrastructure.persistence.slo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 服务等级目标 JPA 实体（表 slo）。
 *
 * @author smartops
 * @since 1.0.0
 */
@Getter
@Setter
@Entity
@Table(name = "slo")
public class SloEntity {

    /** 主键（自增）。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** SLO 名称。 */
    @Column(nullable = false, length = 128)
    private String name;

    /** 服务名称。 */
    @Column(nullable = false, length = 128)
    private String serviceName;

    /** 指标名称。 */
    @Column(length = 128)
    private String metricName;

    /** 目标百分比（如 99.9）。 */
    @Column(nullable = false)
    private double targetPct;

    /** 评估窗口（天）。 */
    @Column(nullable = false)
    private int windowDays;

    /** 错误预算百分比。 */
    @Column(nullable = false)
    private double errorBudgetPct;

    /** 是否启用。 */
    @Column(nullable = false)
    private boolean enabled;
}
