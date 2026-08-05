package com.smartops.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AlertStatus} 枚举测试。
 *
 * <p>验证告警处理状态枚举的完整性与流转顺序。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class AlertStatusTest {

    @Test
    @DisplayName("枚举包含全部 3 个状态")
    void should_containAllStatuses_when_valuesCalled() {
        assertThat(AlertStatus.values()).containsExactly(
                AlertStatus.OPEN,
                AlertStatus.ACKED,
                AlertStatus.RESOLVED
        );
    }

    @Test
    @DisplayName("valueOf 按名称正确返回枚举常量")
    void should_returnCorrectStatus_when_valueOfCalled() {
        assertThat(AlertStatus.valueOf("OPEN")).isEqualTo(AlertStatus.OPEN);
        assertThat(AlertStatus.valueOf("ACKED")).isEqualTo(AlertStatus.ACKED);
        assertThat(AlertStatus.valueOf("RESOLVED")).isEqualTo(AlertStatus.RESOLVED);
    }

    @Test
    @DisplayName("状态 ordinal 按处理流程 OPEN → ACKED → RESOLVED 递增")
    void should_haveAscendingOrdinal_when_inWorkflowOrder() {
        assertThat(AlertStatus.OPEN.ordinal()).isLessThan(AlertStatus.ACKED.ordinal());
        assertThat(AlertStatus.ACKED.ordinal()).isLessThan(AlertStatus.RESOLVED.ordinal());
    }
}
