package com.smartops.common.model;

import com.smartops.common.enums.AgentRole;
import com.smartops.common.enums.IntentType;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Agent 能力卡片。
 *
 * <p>对应 agent.md 阶段三 Agent Card 注册发现机制。每个 Agent 在注册时
 * 声明自己的角色、专长领域、支持的意图类型和并发能力，供 Supervisor
 * 在任务分解后选择最合适的 Worker 执行子任务。</p>
 *
 * <p>线程安全：record 不可变，所有集合字段在构造时做防御性拷贝。</p>
 *
 * @author smartops
 * @since 1.0.0
 *
 * @param agentId          Agent 唯一标识
 * @param role             Agent 角色
 * @param name             显示名称
 * @param description      能力描述
 * @param expertise        专长领域集合
 * @param supportedIntents 支持的意图类型集合
 * @param maxConcurrency   最大并发任务数
 */
public record AgentCard(
        String agentId,
        AgentRole role,
        String name,
        String description,
        Set<String> expertise,
        Set<IntentType> supportedIntents,
        int maxConcurrency
) {

    /**
     * 紧凑构造器：校验必填字段并做防御性拷贝。
     *
     * @param agentId          Agent 唯一标识
     * @param role             Agent 角色
     * @param name             显示名称
     * @param description      能力描述
     * @param expertise        专长领域集合
     * @param supportedIntents 支持的意图类型集合
     * @param maxConcurrency   最大并发任务数
     */
    public AgentCard {
        Objects.requireNonNull(agentId, "agentId 不能为 null");
        Objects.requireNonNull(role, "role 不能为 null");
        if (agentId.isBlank()) {
            throw new IllegalArgumentException("agentId 不能为空白");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name 不能为 null 或空白");
        }
        if (maxConcurrency < 1) {
            throw new IllegalArgumentException("maxConcurrency 必须 >= 1");
        }
        expertise = expertise == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(Set.copyOf(expertise));
        supportedIntents = supportedIntents == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(Set.copyOf(supportedIntents));
    }

    /**
     * 判断该 Agent 是否支持指定的意图类型。
     *
     * @param intentType 意图类型
     * @return 如果支持返回 true
     */
    public boolean supportsIntent(IntentType intentType) {
        return supportedIntents.contains(intentType);
    }

    /**
     * 判断该 Agent 是否具有指定专长。
     *
     * @param skill 专长关键词
     * @return 如果具备该专长返回 true
     */
    public boolean hasExpertise(String skill) {
        return skill != null && expertise.contains(skill);
    }
}
