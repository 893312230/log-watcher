package com.smartops.domain.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link OpsEvent} 单元测试。
 *
 * @author smartops
 * @since 1.0.0
 */
class OpsEventTest {

    @Test
    @DisplayName("of 工厂方法填充类型/载荷/发生时间")
    void should_createEventWithCurrentTime() {
        OpsEvent event = OpsEvent.of(OpsEvent.ALERT_CREATED, Map.of("alertId", 1));

        assertThat(event.type()).isEqualTo("ALERT_CREATED");
        assertThat(event.payload()).containsEntry("alertId", 1);
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    @DisplayName("事件类型常量定义稳定")
    void should_exposeStableTypeConstants() {
        assertThat(OpsEvent.ALERT_ACKED).isEqualTo("ALERT_ACKED");
        assertThat(OpsEvent.RUNBOOK_FAILED).isEqualTo("RUNBOOK_FAILED");
        assertThat(OpsEvent.RUNBOOK_COMPLETED).isEqualTo("RUNBOOK_COMPLETED");
        assertThat(OpsEvent.INCIDENT_POSTMORTEM).isEqualTo("INCIDENT_POSTMORTEM");
    }
}
