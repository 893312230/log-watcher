package com.smartops.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AlertLevel} 枚举测试。
 *
 * <p>验证告警级别枚举的完整性与严重度排序契约。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class AlertLevelTest {

    @Test
    @DisplayName("枚举包含全部 3 个级别")
    void should_containAllLevels_when_valuesCalled() {
        assertThat(AlertLevel.values()).containsExactly(
                AlertLevel.ERROR,
                AlertLevel.WARN,
                AlertLevel.INFO
        );
    }

    @Test
    @DisplayName("valueOf 按名称正确返回枚举常量")
    void should_returnCorrectLevel_when_valueOfCalled() {
        assertThat(AlertLevel.valueOf("ERROR")).isEqualTo(AlertLevel.ERROR);
        assertThat(AlertLevel.valueOf("WARN")).isEqualTo(AlertLevel.WARN);
        assertThat(AlertLevel.valueOf("INFO")).isEqualTo(AlertLevel.INFO);
    }

    @Test
    @DisplayName("严重度 ordinal 按 ERROR > WARN > INFO 排序")
    void should_haveDescendingSeverity_when_ordinalCompared() {
        assertThat(AlertLevel.ERROR.ordinal()).isLessThan(AlertLevel.WARN.ordinal());
        assertThat(AlertLevel.WARN.ordinal()).isLessThan(AlertLevel.INFO.ordinal());
    }
}
