package com.smartops.common.model;

import com.smartops.common.enums.AgentRole;
import com.smartops.common.enums.IntentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AgentCard} 单元测试。
 *
 * <p>验证 Agent 能力卡片的构造、字段校验、防御性拷贝、不可变性及能力判定方法。
 * 对应 agent.md 阶段三 Agent Card 注册发现机制。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class AgentCardTest {

    private static final String AGENT_ID = "agent-monitor-01";
    private static final String NAME = "监控Agent";
    private static final String DESCRIPTION = "负责监控告警";
    private static final Set<String> EXPERTISE = Set.of("metric", "alert");
    private static final Set<IntentType> INTENTS = Set.of(
            IntentType.QUERY_METRIC, IntentType.ANALYZE_ALERT
    );

    @Nested
    @DisplayName("构造与校验")
    class Construction {

        @Test
        @DisplayName("合法参数构造成功且字段全部正确")
        void should_construct_when_validParams() {
            AgentCard card = new AgentCard(
                    AGENT_ID, AgentRole.MONITOR, NAME, DESCRIPTION,
                    EXPERTISE, INTENTS, 5
            );

            assertThat(card.agentId()).isEqualTo(AGENT_ID);
            assertThat(card.role()).isEqualTo(AgentRole.MONITOR);
            assertThat(card.name()).isEqualTo(NAME);
            assertThat(card.description()).isEqualTo(DESCRIPTION);
            assertThat(card.expertise()).containsExactlyInAnyOrder("metric", "alert");
            assertThat(card.supportedIntents())
                    .containsExactlyInAnyOrder(IntentType.QUERY_METRIC, IntentType.ANALYZE_ALERT);
            assertThat(card.maxConcurrency()).isEqualTo(5);
        }

        @Test
        @DisplayName("description 为 null 时构造成功")
        void should_construct_when_descriptionIsNull() {
            AgentCard card = new AgentCard(
                    AGENT_ID, AgentRole.MONITOR, NAME, null,
                    EXPERTISE, INTENTS, 5
            );

            assertThat(card.description()).isNull();
        }

        @Test
        @DisplayName("maxConcurrency 为边界值 1 时构造成功")
        void should_construct_when_maxConcurrencyAtLowerBoundary() {
            AgentCard card = new AgentCard(
                    AGENT_ID, AgentRole.MONITOR, NAME, DESCRIPTION,
                    EXPERTISE, INTENTS, 1
            );

            assertThat(card.maxConcurrency()).isEqualTo(1);
        }

        @Test
        @DisplayName("agentId 为 null 时抛出 NullPointerException")
        void should_throwNpe_when_agentIdIsNull() {
            assertThatThrownBy(() -> new AgentCard(
                    null, AgentRole.MONITOR, NAME, DESCRIPTION,
                    EXPERTISE, INTENTS, 5))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("agentId");
        }

        @Test
        @DisplayName("role 为 null 时抛出 NullPointerException")
        void should_throwNpe_when_roleIsNull() {
            assertThatThrownBy(() -> new AgentCard(
                    AGENT_ID, null, NAME, DESCRIPTION,
                    EXPERTISE, INTENTS, 5))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("role");
        }

        @Test
        @DisplayName("agentId 为空白时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_agentIdIsBlank() {
            assertThatThrownBy(() -> new AgentCard(
                    "   ", AgentRole.MONITOR, NAME, DESCRIPTION,
                    EXPERTISE, INTENTS, 5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("agentId");
        }

        @Test
        @DisplayName("agentId 为空串时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_agentIdIsEmpty() {
            assertThatThrownBy(() -> new AgentCard(
                    "", AgentRole.MONITOR, NAME, DESCRIPTION,
                    EXPERTISE, INTENTS, 5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("agentId");
        }

        @Test
        @DisplayName("name 为 null 时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_nameIsNull() {
            assertThatThrownBy(() -> new AgentCard(
                    AGENT_ID, AgentRole.MONITOR, null, DESCRIPTION,
                    EXPERTISE, INTENTS, 5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("name");
        }

        @Test
        @DisplayName("name 为空白时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_nameIsBlank() {
            assertThatThrownBy(() -> new AgentCard(
                    AGENT_ID, AgentRole.MONITOR, "   ", DESCRIPTION,
                    EXPERTISE, INTENTS, 5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("name");
        }

        @Test
        @DisplayName("maxConcurrency 为 0 时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_maxConcurrencyZero() {
            assertThatThrownBy(() -> new AgentCard(
                    AGENT_ID, AgentRole.MONITOR, NAME, DESCRIPTION,
                    EXPERTISE, INTENTS, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxConcurrency");
        }

        @Test
        @DisplayName("maxConcurrency 为负数时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_maxConcurrencyNegative() {
            assertThatThrownBy(() -> new AgentCard(
                    AGENT_ID, AgentRole.MONITOR, NAME, DESCRIPTION,
                    EXPERTISE, INTENTS, -3))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxConcurrency");
        }
    }

    @Nested
    @DisplayName("null 集合默认空集")
    class NullCollectionDefaults {

        @Test
        @DisplayName("expertise 为 null 时返回空集")
        void should_returnEmptySet_when_expertiseNull() {
            AgentCard card = new AgentCard(
                    AGENT_ID, AgentRole.MONITOR, NAME, DESCRIPTION,
                    null, INTENTS, 5
            );

            assertThat(card.expertise()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("supportedIntents 为 null 时返回空集")
        void should_returnEmptySet_when_supportedIntentsNull() {
            AgentCard card = new AgentCard(
                    AGENT_ID, AgentRole.MONITOR, NAME, DESCRIPTION,
                    EXPERTISE, null, 5
            );

            assertThat(card.supportedIntents()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("expertise 与 supportedIntents 同时为 null 时均返回空集")
        void should_returnEmptySets_when_bothCollectionsNull() {
            AgentCard card = new AgentCard(
                    AGENT_ID, AgentRole.MONITOR, NAME, DESCRIPTION,
                    null, null, 5
            );

            assertThat(card.expertise()).isNotNull().isEmpty();
            assertThat(card.supportedIntents()).isNotNull().isEmpty();
        }
    }

    @Nested
    @DisplayName("不可变性")
    class Immutability {

        @Test
        @DisplayName("返回的 expertise 集合不可修改")
        void should_returnUnmodifiableSet_when_expertiseAccessed() {
            AgentCard card = new AgentCard(
                    AGENT_ID, AgentRole.MONITOR, NAME, DESCRIPTION,
                    EXPERTISE, INTENTS, 5
            );

            assertThatThrownBy(() -> card.expertise().add("newSkill"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("返回的 supportedIntents 集合不可修改")
        void should_returnUnmodifiableSet_when_supportedIntentsAccessed() {
            AgentCard card = new AgentCard(
                    AGENT_ID, AgentRole.MONITOR, NAME, DESCRIPTION,
                    EXPERTISE, INTENTS, 5
            );

            assertThatThrownBy(() -> card.supportedIntents().add(IntentType.ROOT_CAUSE))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("原始 expertise 集合修改不影响 AgentCard")
        void should_notBeAffected_when_originalExpertiseModified() {
            Set<String> mutable = new HashSet<>(EXPERTISE);
            AgentCard card = new AgentCard(
                    AGENT_ID, AgentRole.MONITOR, NAME, DESCRIPTION,
                    mutable, INTENTS, 5
            );

            mutable.add("newSkill");

            assertThat(card.expertise()).hasSize(2).containsExactlyInAnyOrder("metric", "alert");
        }

        @Test
        @DisplayName("原始 supportedIntents 集合修改不影响 AgentCard")
        void should_notBeAffected_when_originalIntentsModified() {
            Set<IntentType> mutable = new HashSet<>(INTENTS);
            AgentCard card = new AgentCard(
                    AGENT_ID, AgentRole.MONITOR, NAME, DESCRIPTION,
                    EXPERTISE, mutable, 5
            );

            mutable.add(IntentType.ROOT_CAUSE);

            assertThat(card.supportedIntents()).hasSize(2)
                    .containsExactlyInAnyOrder(IntentType.QUERY_METRIC, IntentType.ANALYZE_ALERT);
        }
    }

    @Nested
    @DisplayName("supportsIntent 方法")
    class SupportsIntent {

        @Test
        @DisplayName("Agent 支持的意图类型返回 true")
        void should_returnTrue_when_intentIsSupported() {
            AgentCard card = new AgentCard(
                    AGENT_ID, AgentRole.MONITOR, NAME, DESCRIPTION,
                    EXPERTISE, INTENTS, 5
            );

            assertThat(card.supportsIntent(IntentType.QUERY_METRIC)).isTrue();
            assertThat(card.supportsIntent(IntentType.ANALYZE_ALERT)).isTrue();
        }

        @Test
        @DisplayName("Agent 不支持的意图类型返回 false")
        void should_returnFalse_when_intentIsNotSupported() {
            AgentCard card = new AgentCard(
                    AGENT_ID, AgentRole.MONITOR, NAME, DESCRIPTION,
                    EXPERTISE, INTENTS, 5
            );

            assertThat(card.supportsIntent(IntentType.ROOT_CAUSE)).isFalse();
            assertThat(card.supportsIntent(IntentType.EXECUTE_OPERATION)).isFalse();
        }

        @Test
        @DisplayName("supportedIntents 为空时任意意图均返回 false")
        void should_returnFalse_when_intentsEmpty() {
            AgentCard card = new AgentCard(
                    AGENT_ID, AgentRole.MONITOR, NAME, DESCRIPTION,
                    EXPERTISE, null, 5
            );

            assertThat(card.supportsIntent(IntentType.QUERY_METRIC)).isFalse();
        }

        @Test
        @DisplayName("传入 null 意图时抛出 NullPointerException")
        void should_throwNpe_when_intentIsNull() {
            AgentCard card = new AgentCard(
                    AGENT_ID, AgentRole.MONITOR, NAME, DESCRIPTION,
                    EXPERTISE, INTENTS, 5
            );

            assertThatThrownBy(() -> card.supportsIntent(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("hasExpertise 方法")
    class HasExpertise {

        @Test
        @DisplayName("Agent 具备指定专长时返回 true")
        void should_returnTrue_when_hasExpertise() {
            AgentCard card = new AgentCard(
                    AGENT_ID, AgentRole.MONITOR, NAME, DESCRIPTION,
                    EXPERTISE, INTENTS, 5
            );

            assertThat(card.hasExpertise("metric")).isTrue();
            assertThat(card.hasExpertise("alert")).isTrue();
        }

        @Test
        @DisplayName("Agent 不具备指定专长时返回 false")
        void should_returnFalse_when_doesNotHaveExpertise() {
            AgentCard card = new AgentCard(
                    AGENT_ID, AgentRole.MONITOR, NAME, DESCRIPTION,
                    EXPERTISE, INTENTS, 5
            );

            assertThat(card.hasExpertise("deploy")).isFalse();
        }

        @Test
        @DisplayName("传入 null 专长时返回 false")
        void should_returnFalse_when_skillIsNull() {
            AgentCard card = new AgentCard(
                    AGENT_ID, AgentRole.MONITOR, NAME, DESCRIPTION,
                    EXPERTISE, INTENTS, 5
            );

            assertThat(card.hasExpertise(null)).isFalse();
        }

        @Test
        @DisplayName("expertise 为空集时任意专长均返回 false")
        void should_returnFalse_when_expertiseEmpty() {
            AgentCard card = new AgentCard(
                    AGENT_ID, AgentRole.MONITOR, NAME, DESCRIPTION,
                    null, INTENTS, 5
            );

            assertThat(card.hasExpertise("metric")).isFalse();
        }
    }
}
