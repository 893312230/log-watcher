package com.smartops.domain.runbook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link Runbook} 单元测试。
 *
 * @author smartops
 * @since 1.0.0
 */
class RunbookTest {

    @Test
    @DisplayName("steps 为 null 时归一化为空列表，否则防御性复制")
    void should_normalizeSteps() {
        Runbook nullSteps = new Runbook(null, "rb", "d", "K", null, 1, null, true);
        assertThat(nullSteps.steps()).isEmpty();

        Runbook rb = new Runbook(1L, "rb", "d", "K", List.of("s1"), 3, "rb-back", false);
        assertThat(rb.steps()).containsExactly("s1");
        assertThat(rb.safetyLevel()).isEqualTo(3);
    }

    @Test
    @DisplayName("safetyLevel 小于 1 时收敛为 1")
    void should_clampSafetyLevel_when_belowOne() {
        Runbook rb = new Runbook(null, "rb", "d", "K", List.of(), 0, null, true);
        assertThat(rb.safetyLevel()).isEqualTo(1);
    }

    @Test
    @DisplayName("name 为 null 时抛出异常")
    void should_throw_when_nameNull() {
        assertThatThrownBy(() -> new Runbook(null, null, "d", "K", List.of(), 1, null, true))
                .isInstanceOf(NullPointerException.class);
    }
}
