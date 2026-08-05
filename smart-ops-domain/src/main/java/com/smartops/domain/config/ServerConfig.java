package com.smartops.domain.config;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 服务器配置模型（阶段六配置管理）。
 *
 * <p>每个实例代表一个被管理的应用服务器/服务，
 * 包含部署路径、代码库、日志路径等运维关键信息。
 * 告警分析时作为上下文注入 LLM prompt，使修复建议可定位到具体服务器和代码位置。</p>
 *
 * @author smartops
 * @since 1.0.0
 *
 * @param id          持久化 id，未落库为 null
 * @param name        服务名称
 * @param host        服务器地址
 * @param deployPath  应用部署路径
 * @param codeRepo    代码库路径
 * @param logPath     日志文件路径
 * @param description 描述
 * @param tags        标签
 * @param createdAt   创建时间
 * @param updatedAt   更新时间
 */
public record ServerConfig(
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

    /** name 最大长度。 */
    public static final int NAME_MAX_LENGTH = 128;

    public ServerConfig {
        Objects.requireNonNull(name, "服务名称不能为 null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("服务名称不能为空");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("服务名称最长 " + NAME_MAX_LENGTH + " 字符");
        }
        tags = tags == null ? Collections.emptyList()
                : Collections.unmodifiableList(List.copyOf(tags));
    }

    public static ServerConfig create(String name, String host, String deployPath,
                                       String codeRepo, String logPath,
                                       String description, List<String> tags,
                                       Instant now) {
        return new ServerConfig(null, name, host, deployPath, codeRepo, logPath,
                description, tags, now, now);
    }

    public ServerConfig withId(long newId) {
        return new ServerConfig(newId, name, host, deployPath, codeRepo, logPath,
                description, tags, createdAt, updatedAt);
    }
}
