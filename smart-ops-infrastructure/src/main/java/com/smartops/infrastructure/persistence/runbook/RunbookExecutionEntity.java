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
 * Runbook 执行记录 JPA 实体（表 runbook_execution）。
 *
 * @author smartops
 * @since 1.0.0
 */
@Getter
@Setter
@Entity
@Table(name = "runbook_execution")
public class RunbookExecutionEntity {

    /** 主键（自增）。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属 Runbook id。 */
    @Column(nullable = false)
    private Long runbookId;

    /** 开始时间。 */
    @Column(nullable = false)
    private Instant startedAt;

    /** 结束时间（运行中为 null）。 */
    private Instant finishedAt;

    /** 执行状态（RUNNING / SUCCESS / FAILED）。 */
    @Column(nullable = false, length = 16)
    private String status;
}
