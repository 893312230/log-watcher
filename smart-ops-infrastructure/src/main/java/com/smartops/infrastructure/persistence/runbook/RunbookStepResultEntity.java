package com.smartops.infrastructure.persistence.runbook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Runbook 单步执行结果 JPA 实体（表 runbook_step_result）。
 *
 * @author smartops
 * @since 1.0.0
 */
@Getter
@Setter
@Entity
@Table(name = "runbook_step_result")
public class RunbookStepResultEntity {

    /** 主键（自增）。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属执行记录 id。 */
    @Column(nullable = false)
    private Long executionId;

    /** 步骤序号（从 1 开始）。 */
    @Column(nullable = false)
    private int seq;

    /** 步骤原始指令文本。 */
    @Column(columnDefinition = "TEXT")
    private String command;

    /** 执行状态（SUCCESS / FAILED / SKIPPED）。 */
    @Column(nullable = false, length = 16)
    private String status;

    /** 执行输出或错误信息。 */
    @Column(columnDefinition = "TEXT")
    private String output;
}
