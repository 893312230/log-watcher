package com.smartops.domain.slo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ServiceLevelObjective} 单元测试。
 *
 * @author smartops
 * @since 1.0.0
 */
class ServiceLevelObjectiveTest {

    @Test
    @DisplayName("目标百分比越界时收敛为 99.9")
    void should_clampTargetPercent_when_outOfRange() {
        assertThat(new ServiceLevelObjective(null, "s", "svc", "m", 0, 7, 0.1, true)
                .targetPercent()).isEqualTo(99.9);
        assertThat(new ServiceLevelObjective(null, "s", "svc", "m", 101, 7, 0.1, true)
                .targetPercent()).isEqualTo(99.9);
        assertThat(new ServiceLevelObjective(null, "s", "svc", "m", 99.5, 7, 0.1, true)
                .targetPercent()).isEqualTo(99.5);
    }

    @Test
    @DisplayName("统计窗口小于 1 天时收敛为 30 天")
    void should_clampWindowDays_when_belowOne() {
        assertThat(new ServiceLevelObjective(null, "s", "svc", "m", 99.9, 0, 0.1, true)
                .windowDays()).isEqualTo(30);
        assertThat(new ServiceLevelObjective(null, "s", "svc", "m", 99.9, 7, 0.1, true)
                .windowDays()).isEqualTo(7);
    }

    @Test
    @DisplayName("name 为 null 时抛出异常")
    void should_throw_when_nameNull() {
        assertThatThrownBy(() -> new ServiceLevelObjective(null, null, "svc", "m", 99.9, 7, 0.1, true))
                .isInstanceOf(NullPointerException.class);
    }
}
