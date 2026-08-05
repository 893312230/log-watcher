package com.smartops.api.controller;

import com.smartops.api.dto.AlertView;
import com.smartops.common.enums.AlertLevel;
import com.smartops.domain.logwatch.Alert;
import com.smartops.infrastructure.logwatch.impl.SseAlertNotifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AlertSseController} 单元测试。
 *
 * <p>覆盖：告警实时下发为 AlertView、心跳注释行按间隔推送、持续流不完成。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class AlertSseControllerTest {

    private static final Duration HEARTBEAT = Duration.ofMillis(100);

    private Alert alert(String fingerprint) {
        return Alert.create(fingerprint + "-extra-long-tail", "app.log", AlertLevel.ERROR,
                "ERROR", "摘要", "堆栈", 3, Instant.now());
    }

    @Test
    @DisplayName("发布的告警以 AlertView 形式实时下发（指纹截断 12 位）")
    void should_pushAlertView_when_alertPublished() {
        SseAlertNotifier notifier = new SseAlertNotifier(8);
        AlertSseController controller = new AlertSseController(notifier, Duration.ofHours(1));

        StepVerifier.create(controller.stream().take(1))
                .then(() -> notifier.publish(alert("fp-1")))
                .assertNext(event -> {
                    assertThat(event).isInstanceOf(AlertView.class);
                    AlertView view = (AlertView) event;
                    assertThat(view.fingerprint()).isEqualTo("fp-1-extra-l");
                    assertThat(view.source()).isEqualTo("app.log");
                    assertThat(view.level()).isEqualTo(AlertLevel.ERROR);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("空闲期按心跳间隔推送 SSE 注释行")
    void should_emitHeartbeat_when_idle() {
        SseAlertNotifier notifier = new SseAlertNotifier(8);
        AlertSseController controller = new AlertSseController(notifier, HEARTBEAT);

        StepVerifier.create(controller.stream().take(2))
                .expectNext(AlertSseController.HEARTBEAT_MARKER, AlertSseController.HEARTBEAT_MARKER)
                .verifyComplete();
    }

    @Test
    @DisplayName("流为持续流，心跳后不自动完成")
    void should_stayOpen_when_heartbeatEmitted() {
        SseAlertNotifier notifier = new SseAlertNotifier(8);
        AlertSseController controller = new AlertSseController(notifier, HEARTBEAT);

        StepVerifier.create(controller.stream())
                .expectNext(AlertSseController.HEARTBEAT_MARKER)
                .thenAwait(Duration.ofMillis(200))
                .thenCancel()
                .verify();
    }
}
