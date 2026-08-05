package com.smartops.infrastructure.persistence.config;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 服务器配置 JPA Repository。
 *
 * @author smartops
 * @since 1.0.0
 */
public interface ServerConfigJpaRepository extends JpaRepository<ServerConfigEntity, Long> {

    Optional<ServerConfigEntity> findByName(String name);
}
