package com.smartops.infrastructure.persistence.config;

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
 * 服务器配置 JPA 实体（表 server_config）。
 *
 * @author smartops
 * @since 1.0.0
 */
@Getter
@Setter
@Entity
@Table(name = "server_config")
public class ServerConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128, unique = true)
    private String name;

    @Column(length = 256)
    private String host;

    @Column(length = 512)
    private String deployPath;

    @Column(length = 512)
    private String codeRepo;

    @Column(length = 512)
    private String logPath;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 512)
    private String tags;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}
