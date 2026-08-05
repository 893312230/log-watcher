package com.smartops.infrastructure.persistence.config.impl;

import com.smartops.domain.config.ServerConfig;
import com.smartops.infrastructure.persistence.config.ServerConfigJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ServerConfigRepositoryImpl} 持久层测试。
 *
 * @author smartops
 * @since 1.0.0
 */
@DataJpaTest
class ServerConfigRepositoryImplTest {

    @Autowired
    private ServerConfigJpaRepository jpa;

    private ServerConfigRepositoryImpl repo;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        repo = new ServerConfigRepositoryImpl(jpa);
    }

    @Test
    @DisplayName("save 后 findAll 可查到")
    void should_findAfterSave() {
        ServerConfig c = ServerConfig.create("test-svc", "10.0.0.1",
                "/opt/test.jar", null, null, null, null, Instant.now());
        repo.save(c);

        assertThat(repo.findAll()).hasSize(1);
        assertThat(repo.findByName("test-svc")).isPresent();
    }

    @Test
    @DisplayName("findById 命中")
    void should_findById() {
        ServerConfig c = ServerConfig.create("svc2", null, null, null, null, null, null, Instant.now());
        ServerConfig saved = repo.save(c);
        assertThat(repo.findById(saved.id())).isPresent();
    }

    @Test
    @DisplayName("deleteById 后不可查")
    void should_notFindAfterDelete() {
        ServerConfig c = ServerConfig.create("svc3", null, null, null, null, null, null, Instant.now());
        ServerConfig saved = repo.save(c);
        repo.deleteById(saved.id());
        assertThat(repo.findById(saved.id())).isEmpty();
    }
}
