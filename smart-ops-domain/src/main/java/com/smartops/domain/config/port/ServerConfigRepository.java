package com.smartops.domain.config.port;

import com.smartops.domain.config.ServerConfig;

import java.util.List;
import java.util.Optional;

/**
 * 服务器配置持久化端口（阶段六配置管理）。
 *
 * @author smartops
 * @since 1.0.0
 */
public interface ServerConfigRepository {

    List<ServerConfig> findAll();

    Optional<ServerConfig> findById(long id);

    Optional<ServerConfig> findByName(String name);

    ServerConfig save(ServerConfig config);

    void deleteById(long id);
}
