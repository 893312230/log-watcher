package com.smartops.infrastructure.persistence.config.impl;

import com.smartops.domain.config.ServerConfig;
import com.smartops.domain.config.port.ServerConfigRepository;
import com.smartops.infrastructure.persistence.config.ServerConfigEntity;
import com.smartops.infrastructure.persistence.config.ServerConfigJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * {@link ServerConfigRepository} JPA 实现。
 *
 * @author smartops
 * @since 1.0.0
 */
@Repository
public class ServerConfigRepositoryImpl implements ServerConfigRepository {

    private final ServerConfigJpaRepository jpa;

    public ServerConfigRepositoryImpl(ServerConfigJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<ServerConfig> findAll() {
        return jpa.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<ServerConfig> findById(long id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<ServerConfig> findByName(String name) {
        return jpa.findByName(name).map(this::toDomain);
    }

    @Override
    public ServerConfig save(ServerConfig config) {
        ServerConfigEntity entity = toEntity(config);
        return toDomain(jpa.save(entity));
    }

    @Override
    public void deleteById(long id) {
        jpa.deleteById(id);
    }

    private ServerConfig toDomain(ServerConfigEntity e) {
        List<String> tagList = e.getTags() != null && !e.getTags().isBlank()
                ? Arrays.asList(e.getTags().split(","))
                : Collections.emptyList();
        return new ServerConfig(e.getId(), e.getName(), e.getHost(),
                e.getDeployPath(), e.getCodeRepo(), e.getLogPath(),
                e.getDescription(), tagList, e.getCreatedAt(), e.getUpdatedAt());
    }

    private ServerConfigEntity toEntity(ServerConfig c) {
        ServerConfigEntity e = new ServerConfigEntity();
        e.setId(c.id());
        e.setName(c.name());
        e.setHost(c.host());
        e.setDeployPath(c.deployPath());
        e.setCodeRepo(c.codeRepo());
        e.setLogPath(c.logPath());
        e.setDescription(c.description());
        e.setTags(c.tags() != null ? String.join(",", c.tags()) : null);
        e.setCreatedAt(c.createdAt());
        e.setUpdatedAt(c.updatedAt());
        return e;
    }
}
