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
 * Runbook 步骤 JPA 实体（表 runbook_step）。
 *
 * @author smartops
 * @since 1.0.0
 */
@Getter
@Setter
@Entity
@Table(name = "runbook_step")
public class RunbookStepEntity {

    /** 主键（自增）。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属 Runbook id。 */
    @Column(nullable = false)
    private Long runbookId;

    /** 步骤序号（从 1 开始）。 */
    @Column(nullable = false)
    private int seq;

    /** 步骤类型（HTTP / WEBHOOK / LLM / SCRIPT）。 */
    @Column(nullable = false, length = 16)
    private String stepType;

    /** 步骤配置（当前为原始指令文本，后续演进为 JSON）。 */
    @Column(columnDefinition = "TEXT")
    private String configJson;

    /** 执行条件表达式（预留）。 */
    @Column(length = 512)
    private String conditionExpr;
}
