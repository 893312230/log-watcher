package com.smartops.infrastructure.persistence.runbook;

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
 * Runbook 定义 JPA 实体（表 runbook）。
 *
 * <p>生产环境 ddl-auto=validate，表结构由 docs/sql/V6__runbook.sql 创建。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Getter
@Setter
@Entity
@Table(name = "runbook")
public class RunbookEntity {

    /** 主键（自增）。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 名称。 */
    @Column(nullable = false, length = 128)
    private String name;

    /** 描述。 */
    @Column(length = 512)
    private String description;

    /** 触发关键字。 */
    @Column(length = 128)
    private String triggerKeyword;

    /** 安全等级（1-5，≥4 需审批）。 */
    @Column(nullable = false)
    private int safetyLevel;

    /** 回滚步骤（JSON 数组文本）。 */
    @Column(columnDefinition = "TEXT")
    private String rollbackStepsJson;

    /** 是否启用。 */
    @Column(nullable = false)
    private boolean enabled;

    /** 创建时间。 */
    @Column(nullable = false)
    private Instant createdAt;
}
