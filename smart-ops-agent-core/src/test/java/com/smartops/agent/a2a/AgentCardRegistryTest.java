package com.smartops.agent.a2a;

import com.smartops.common.enums.AgentRole;
import com.smartops.common.enums.IntentType;
import com.smartops.common.model.AgentCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AgentCardRegistry} 单元测试。
 *
 * <p>验证 Agent Card 注册中心的核心契约：
 * <ul>
 *   <li>注册/注销/覆盖能力卡片</li>
 *   <li>按 ID、角色、意图类型、Worker 角色查询</li>
 *   <li>边界条件：null 参数、空注册中心、不存在项</li>
 *   <li>线程安全集合操作的语义正确性</li>
 * </ul></p>
 *
 * <p>对应 agent.md 阶段三 Agent Card 注册发现机制。</p>
 *
 * <p><b>测试策略</b>：
 * <ul>
 *   <li>AgentCardRegistry 无外部依赖，使用真实实例进行纯单元测试</li>
 *   <li>使用 AssertJ 断言，遵循 Arrange-Act-Assert 三段式</li>
 *   <li>使用 {@code @Nested} 分组，{@code @DisplayName} 中文描述</li>
 *   <li>测试方法命名 {@code should_{期望行为}_when_{前置条件}}</li>
 * </ul></p>
 *
 * @author smartops
 * @since 1.0.0
 */
class AgentCardRegistryTest {

    private AgentCardRegistry registry;

    private AgentCard monitorCard;
    private AgentCard analyzeCard;
    private AgentCard supervisorCard;

    @BeforeEach
    void setUp() {
        registry = new AgentCardRegistry();
        monitorCard = new AgentCard(
                "monitor-agent", AgentRole.MONITOR, "监控Agent",
                "实时监控、告警查询、指标趋势分析",
                Set.of("prometheus", "metrics", "alerts", "trends"),
                Set.of(IntentType.QUERY_METRIC, IntentType.TREND_ANALYSIS, IntentType.ANALYZE_ALERT),
                5);
        analyzeCard = new AgentCard(
                "analyze-agent", AgentRole.ANALYZE, "分析Agent",
                "根因分析、日志分析、异常检测",
                Set.of("root-cause", "logs", "traces", "anomaly-detection"),
                Set.of(IntentType.ROOT_CAUSE, IntentType.ANALYZE_ALERT),
                3);
        supervisorCard = new AgentCard(
                "supervisor-agent", AgentRole.SUPERVISOR, "主管Agent",
                "任务分解、Worker 分配、结果聚合",
                Set.of("orchestration"),
                Set.of(),
                1);
    }

    @Nested
    @DisplayName("register 注册")
    class Register {

        @Test
        @DisplayName("正常注册 Agent 卡片")
        void should_registerCard_when_cardIsValid() {
            registry.register(monitorCard);

            assertThat(registry.size()).isEqualTo(1);
            assertThat(registry.isRegistered("monitor-agent")).isTrue();
            assertThat(registry.findById("monitor-agent")).isEqualTo(monitorCard);
        }

        @Test
        @DisplayName("注册 null 卡片抛出 NullPointerException")
        void should_throwNpe_when_cardIsNull() {
            assertThatThrownBy(() -> registry.register(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("card");
        }

        @Test
        @DisplayName("重复注册相同 agentId 覆盖原卡片")
        void should_overwriteCard_when_agentIdAlreadyExists() {
            registry.register(monitorCard);

            AgentCard updatedCard = new AgentCard(
                    "monitor-agent", AgentRole.MONITOR, "新监控Agent",
                    "更新后的描述",
                    Set.of("new-tag"),
                    Set.of(IntentType.QUERY_METRIC),
                    10);
            registry.register(updatedCard);

            assertThat(registry.size()).isEqualTo(1);
            assertThat(registry.findById("monitor-agent")).isEqualTo(updatedCard);
            assertThat(registry.findById("monitor-agent").name()).isEqualTo("新监控Agent");
        }

        @Test
        @DisplayName("注册多个不同 Agent 后数量正确")
        void should_incrementSize_when_multipleCardsRegistered() {
            registry.register(monitorCard);
            registry.register(analyzeCard);
            registry.register(supervisorCard);

            assertThat(registry.size()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("unregister 注销")
    class Unregister {

        @Test
        @DisplayName("正常注销已注册的 Agent")
        void should_unregisterCard_when_agentIdExists() {
            registry.register(monitorCard);
            assertThat(registry.size()).isEqualTo(1);

            registry.unregister("monitor-agent");

            assertThat(registry.size()).isEqualTo(0);
            assertThat(registry.isRegistered("monitor-agent")).isFalse();
            assertThat(registry.findById("monitor-agent")).isNull();
        }

        @Test
        @DisplayName("注销不存在的 agentId 不抛异常")
        void should_notThrow_when_agentIdNotExists() {
            registry.unregister("non-existent");

            assertThat(registry.size()).isEqualTo(0);
        }

        @Test
        @DisplayName("注销 null agentId 直接返回不抛异常")
        void should_returnDirectly_when_agentIdIsNull() {
            registry.register(monitorCard);

            registry.unregister(null);

            assertThat(registry.size()).isEqualTo(1);
            assertThat(registry.isRegistered("monitor-agent")).isTrue();
        }
    }

    @Nested
    @DisplayName("findById 按 ID 查询")
    class FindById {

        @Test
        @DisplayName("存在的 agentId 返回对应卡片")
        void should_returnCard_when_agentIdExists() {
            registry.register(monitorCard);

            AgentCard result = registry.findById("monitor-agent");

            assertThat(result).isEqualTo(monitorCard);
        }

        @Test
        @DisplayName("不存在的 agentId 返回 null")
        void should_returnNull_when_agentIdNotExists() {
            assertThat(registry.findById("non-existent")).isNull();
        }

        @Test
        @DisplayName("null agentId 返回 null")
        void should_returnNull_when_agentIdIsNull() {
            assertThat(registry.findById(null)).isNull();
        }
    }

    @Nested
    @DisplayName("findByRole 按角色查询")
    class FindByRole {

        @Test
        @DisplayName("返回指定角色的所有卡片")
        void should_returnCards_when_roleMatches() {
            registry.register(monitorCard);
            registry.register(analyzeCard);
            registry.register(supervisorCard);

            List<AgentCard> result = registry.findByRole(AgentRole.MONITOR);

            assertThat(result).hasSize(1);
            assertThat(result).contains(monitorCard);
        }

        @Test
        @DisplayName("无匹配角色时返回空列表")
        void should_returnEmptyList_when_noMatch() {
            registry.register(monitorCard);

            List<AgentCard> result = registry.findByRole(AgentRole.EXECUTE);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null role 返回空列表")
        void should_returnEmptyList_when_roleIsNull() {
            registry.register(monitorCard);

            assertThat(registry.findByRole(null)).isEmpty();
        }

        @Test
        @DisplayName("同角色多 Agent 时全部返回")
        void should_returnAllCards_when_multipleSameRole() {
            AgentCard anotherMonitor = new AgentCard(
                    "monitor-agent-2", AgentRole.MONITOR, "监控Agent2",
                    "另一个监控Agent", Set.of("test"),
                    Set.of(IntentType.QUERY_METRIC), 3);
            registry.register(monitorCard);
            registry.register(anotherMonitor);

            List<AgentCard> result = registry.findByRole(AgentRole.MONITOR);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(AgentCard::agentId)
                    .containsExactlyInAnyOrder("monitor-agent", "monitor-agent-2");
        }
    }

    @Nested
    @DisplayName("findByIntent 按意图查询")
    class FindByIntent {

        @Test
        @DisplayName("返回支持指定意图的所有 Agent 卡片")
        void should_returnCards_when_intentSupportedByMultiple() {
            registry.register(monitorCard);
            registry.register(analyzeCard);

            List<AgentCard> result = registry.findByIntent(IntentType.ANALYZE_ALERT);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(AgentCard::agentId)
                    .containsExactlyInAnyOrder("monitor-agent", "analyze-agent");
        }

        @Test
        @DisplayName("仅监控 Agent 支持 QUERY_METRIC 意图")
        void should_returnOnlyMonitor_when_queryMetricIntent() {
            registry.register(monitorCard);
            registry.register(analyzeCard);

            List<AgentCard> result = registry.findByIntent(IntentType.QUERY_METRIC);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).agentId()).isEqualTo("monitor-agent");
        }

        @Test
        @DisplayName("无 Agent 支持指定意图时返回空列表")
        void should_returnEmptyList_when_noAgentSupportsIntent() {
            registry.register(monitorCard);
            registry.register(analyzeCard);

            List<AgentCard> result = registry.findByIntent(IntentType.EXECUTE_OPERATION);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null intent 返回空列表")
        void should_returnEmptyList_when_intentIsNull() {
            registry.register(monitorCard);

            assertThat(registry.findByIntent(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAllWorkers 查询所有 Worker")
    class FindAllWorkers {

        @Test
        @DisplayName("仅返回 Worker 角色，排除 Supervisor")
        void should_returnOnlyWorkers_when_supervisorExists() {
            registry.register(monitorCard);
            registry.register(analyzeCard);
            registry.register(supervisorCard);

            List<AgentCard> workers = registry.findAllWorkers();

            assertThat(workers).hasSize(2);
            assertThat(workers).extracting(AgentCard::role)
                    .doesNotContain(AgentRole.SUPERVISOR);
            assertThat(workers).extracting(AgentCard::agentId)
                    .containsExactlyInAnyOrder("monitor-agent", "analyze-agent");
        }

        @Test
        @DisplayName("注册中心为空时返回空列表")
        void should_returnEmptyList_when_registryEmpty() {
            assertThat(registry.findAllWorkers()).isEmpty();
        }

        @Test
        @DisplayName("仅注册 Supervisor 时返回空列表")
        void should_returnEmptyList_when_onlySupervisorRegistered() {
            registry.register(supervisorCard);

            assertThat(registry.findAllWorkers()).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAll 查询全部")
    class FindAll {

        @Test
        @DisplayName("返回所有已注册卡片")
        void should_returnAllCards_when_registryHasCards() {
            registry.register(monitorCard);
            registry.register(analyzeCard);

            Collection<AgentCard> all = registry.findAll();

            assertThat(all).hasSize(2);
            assertThat(all).contains(monitorCard, analyzeCard);
        }

        @Test
        @DisplayName("注册中心为空时返回空集合")
        void should_returnEmptyCollection_when_registryEmpty() {
            assertThat(registry.findAll()).isEmpty();
        }

        @Test
        @DisplayName("返回的集合为不可变快照")
        void should_returnImmutableSnapshot_when_findAllInvoked() {
            registry.register(monitorCard);

            Collection<AgentCard> snapshot = registry.findAll();

            registry.register(analyzeCard);

            assertThat(snapshot).hasSize(1);
            assertThat(registry.findAll()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("size 与 isRegistered")
    class SizeAndIsRegistered {

        @Test
        @DisplayName("size 返回已注册数量")
        void should_returnCorrectSize_when_cardsRegistered() {
            assertThat(registry.size()).isEqualTo(0);

            registry.register(monitorCard);
            assertThat(registry.size()).isEqualTo(1);

            registry.register(analyzeCard);
            assertThat(registry.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("isRegistered 已注册返回 true")
        void should_returnTrue_when_agentRegistered() {
            registry.register(monitorCard);

            assertThat(registry.isRegistered("monitor-agent")).isTrue();
        }

        @Test
        @DisplayName("isRegistered 未注册返回 false")
        void should_returnFalse_when_agentNotRegistered() {
            assertThat(registry.isRegistered("non-existent")).isFalse();
        }

        @Test
        @DisplayName("isRegistered null 返回 false")
        void should_returnFalse_when_agentIdIsNull() {
            assertThat(registry.isRegistered(null)).isFalse();
        }

        @Test
        @DisplayName("注销后 isRegistered 返回 false")
        void should_returnFalse_when_agentUnregistered() {
            registry.register(monitorCard);
            registry.unregister("monitor-agent");

            assertThat(registry.isRegistered("monitor-agent")).isFalse();
        }
    }

    @Nested
    @DisplayName("clear 清空")
    class Clear {

        @Test
        @DisplayName("清空所有注册的 Agent")
        void should_clearAll_when_clearInvoked() {
            registry.register(monitorCard);
            registry.register(analyzeCard);
            registry.register(supervisorCard);

            registry.clear();

            assertThat(registry.size()).isEqualTo(0);
            assertThat(registry.findAll()).isEmpty();
            assertThat(registry.findAllWorkers()).isEmpty();
        }

        @Test
        @DisplayName("清空空注册中心不抛异常")
        void should_notThrow_when_clearEmptyRegistry() {
            registry.clear();

            assertThat(registry.size()).isEqualTo(0);
        }

        @Test
        @DisplayName("清空后可重新注册 Agent")
        void should_allowReregister_when_clearedAndReregistered() {
            registry.register(monitorCard);
            registry.clear();

            registry.register(analyzeCard);

            assertThat(registry.size()).isEqualTo(1);
            assertThat(registry.isRegistered("analyze-agent")).isTrue();
            assertThat(registry.isRegistered("monitor-agent")).isFalse();
        }
    }
}
