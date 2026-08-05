package com.smartops.agent.a2a;

import com.smartops.common.enums.AgentRole;
import com.smartops.common.enums.IntentType;
import com.smartops.common.model.AgentCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Agent Card 注册中心。
 *
 * <p>对应 agent.md 阶段三 Agent Card 注册发现机制。维护所有已注册 Agent
 * 的能力卡片，支持按角色、意图类型、专长查询。</p>
 *
 * <p>线程安全：内部使用 {@link ConcurrentHashMap}，支持并发注册与查询。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Component
public class AgentCardRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentCardRegistry.class);

    /** 按 agentId 索引的注册表。 */
    private final Map<String, AgentCard> cardsById = new ConcurrentHashMap<>();

    /**
     * 注册 Agent 能力卡片。
     *
     * <p>若 agentId 已存在则覆盖原卡片。</p>
     *
     * @param card Agent 能力卡片
     * @throws NullPointerException 当 card 为 null 时
     */
    public void register(AgentCard card) {
        Objects.requireNonNull(card, "card 不能为 null");
        cardsById.put(card.agentId(), card);
        log.info("Agent 注册成功: agentId={}, role={}, name={}",
                card.agentId(), card.role(), card.name());
    }

    /**
     * 注销 Agent。
     *
     * @param agentId Agent 唯一标识
     */
    public void unregister(String agentId) {
        if (agentId == null) {
            return;
        }
        AgentCard removed = cardsById.remove(agentId);
        if (removed != null) {
            log.info("Agent 注销成功: agentId={}, name={}", agentId, removed.name());
        }
    }

    /**
     * 按 agentId 查询 Agent 能力卡片。
     *
     * @param agentId Agent 唯一标识
     * @return 能力卡片，未找到返回 null
     */
    public AgentCard findById(String agentId) {
        if (agentId == null) {
            return null;
        }
        return cardsById.get(agentId);
    }

    /**
     * 按角色查询所有匹配的 Agent。
     *
     * @param role Agent 角色
     * @return 匹配的能力卡片列表，可能为空
     */
    public List<AgentCard> findByRole(AgentRole role) {
        if (role == null) {
            return List.of();
        }
        return cardsById.values().stream()
                .filter(card -> card.role() == role)
                .collect(Collectors.toList());
    }

    /**
     * 查询支持指定意图类型的所有 Agent。
     *
     * @param intentType 意图类型
     * @return 匹配的能力卡片列表
     */
    public List<AgentCard> findByIntent(IntentType intentType) {
        if (intentType == null) {
            return List.of();
        }
        return cardsById.values().stream()
                .filter(card -> card.supportsIntent(intentType))
                .collect(Collectors.toList());
    }

    /**
     * 查询所有 Worker 角色的 Agent（非 Supervisor）。
     *
     * @return Worker 能力卡片列表
     */
    public List<AgentCard> findAllWorkers() {
        return cardsById.values().stream()
                .filter(card -> card.role().isWorker())
                .collect(Collectors.toList());
    }

    /**
     * 查询所有已注册的 Agent。
     *
     * @return 全部能力卡片
     */
    public Collection<AgentCard> findAll() {
        return List.copyOf(cardsById.values());
    }

    /**
     * 获取已注册的 Agent 数量。
     *
     * @return 注册数量
     */
    public int size() {
        return cardsById.size();
    }

    /**
     * 判断指定 Agent 是否已注册。
     *
     * @param agentId Agent 唯一标识
     * @return 如果已注册返回 true
     */
    public boolean isRegistered(String agentId) {
        return agentId != null && cardsById.containsKey(agentId);
    }

    /**
     * 清空所有注册的 Agent。
     */
    public void clear() {
        cardsById.clear();
        log.info("Agent 注册中心已清空");
    }
}
