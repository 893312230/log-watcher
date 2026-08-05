package com.smartops.infrastructure.persistence.integration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 第三方集成配置 JPA 实体（表 integration）。
 *
 * @author smartops
 * @since 1.0.0
 */
@Getter
@Setter
@Entity
@Table(name = "integration")
public class IntegrationEntity {

    /** 主键（自增）。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 集成类型（JIRA / GITHUB / GITLAB / WEBHOOK）。 */
    @Column(nullable = false, length = 32)
    private String type;

    /** 集成名称。 */
    @Column(nullable = false, length = 128)
    private String name;

    /** 配置（JSON 文本）。 */
    @Column(columnDefinition = "TEXT")
    private String configJson;

    /** 是否启用。 */
    @Column(nullable = false)
    private boolean enabled;
}
