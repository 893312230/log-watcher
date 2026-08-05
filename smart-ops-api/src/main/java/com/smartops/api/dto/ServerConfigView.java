package com.smartops.api.dto;

import com.smartops.domain.config.ServerConfig;

import java.time.Instant;
import java.util.List;

/**
 * 服务器配置视图 DTO。
 *
 * @author smartops
 * @since 1.0.0
 *
 * @param id          服务器 id
 * @param name        服务名称
 * @param host        服务器地址
 * @param deployPath  部署路径
 * @param codeRepo    代码库路径
 * @param logPath     日志路径
 * @param description 描述
 * @param tags        标签
 * @param createdAt   创建时间
 * @param updatedAt   更新时间
 */
public record ServerConfigView(
        Long id,
        String name,
        String host,
        String deployPath,
        String codeRepo,
        String logPath,
        String description,
        List<String> tags,
        Instant createdAt,
        Instant updatedAt
) {

    public static ServerConfigView from(ServerConfig c) {
        return new ServerConfigView(c.id(), c.name(), c.host(), c.deployPath(),
                c.codeRepo(), c.logPath(), c.description(), c.tags(),
                c.createdAt(), c.updatedAt());
    }
}
