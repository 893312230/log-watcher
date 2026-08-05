package com.smartops.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SecurityLevel} 单元测试。
 *
 * <p>验证安全等级的数值、覆盖关系等核心契约，
 * 对应 agent.md 第六章 6.3 节测试编写规范（Arrange-Act-Assert 三段式）。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class SecurityLevelTest {

    @Nested
    @DisplayName("getLevel 方法")
    class GetLevel {

        @Test
        @DisplayName("L0 等级数值为 0")
        void should_returnZero_when_levelIsL0() {
            // Arrange
            SecurityLevel level = SecurityLevel.L0_INPUT_FILTER;

            // Act
            int result = level.getLevel();

            // Assert
            assertThat(result).isEqualTo(0);
        }

        @Test
        @DisplayName("L3 等级数值为 3")
        void should_returnThree_when_levelIsL3() {
            SecurityLevel level = SecurityLevel.L3_HUMAN_CONFIRM;

            int result = level.getLevel();

            assertThat(result).isEqualTo(3);
        }

        @Test
        @DisplayName("等级数值随等级递增")
        void should_increaseMonotonically_when_levelAscends() {
            assertThat(SecurityLevel.L0_INPUT_FILTER.getLevel()).isLessThan(SecurityLevel.L1_PERMISSION_CHECK.getLevel());
            assertThat(SecurityLevel.L1_PERMISSION_CHECK.getLevel()).isLessThan(SecurityLevel.L2_AUDIT_LOG.getLevel());
            assertThat(SecurityLevel.L2_AUDIT_LOG.getLevel()).isLessThan(SecurityLevel.L3_HUMAN_CONFIRM.getLevel());
        }
    }

    @Nested
    @DisplayName("covers 方法")
    class Covers {

        @Test
        @DisplayName("L3 覆盖 L0/L1/L2")
        void should_returnTrue_when_l3CoversLowerLevels() {
            SecurityLevel high = SecurityLevel.L3_HUMAN_CONFIRM;

            assertThat(high.covers(SecurityLevel.L0_INPUT_FILTER)).isTrue();
            assertThat(high.covers(SecurityLevel.L1_PERMISSION_CHECK)).isTrue();
            assertThat(high.covers(SecurityLevel.L2_AUDIT_LOG)).isTrue();
            assertThat(high.covers(SecurityLevel.L3_HUMAN_CONFIRM)).isTrue();
        }

        @Test
        @DisplayName("L0 不覆盖 L1/L2/L3")
        void should_returnFalse_when_l0DoesNotCoverHigherLevels() {
            SecurityLevel low = SecurityLevel.L0_INPUT_FILTER;

            assertThat(low.covers(SecurityLevel.L1_PERMISSION_CHECK)).isFalse();
            assertThat(low.covers(SecurityLevel.L2_AUDIT_LOG)).isFalse();
            assertThat(low.covers(SecurityLevel.L3_HUMAN_CONFIRM)).isFalse();
        }

        @Test
        @DisplayName("同级互相覆盖")
        void should_returnTrue_when_sameLevel() {
            assertThat(SecurityLevel.L1_PERMISSION_CHECK.covers(SecurityLevel.L1_PERMISSION_CHECK)).isTrue();
        }

        @Test
        @DisplayName("目标等级为 null 时抛出 IllegalArgumentException")
        void should_throwIllegalArgumentException_when_targetIsNull() {
            SecurityLevel level = SecurityLevel.L0_INPUT_FILTER;

            assertThatThrownBy(() -> level.covers(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不能为 null");
        }
    }
}
