package com.smartops.infrastructure.logwatch.impl;

import com.smartops.common.enums.AlertLevel;
import com.smartops.domain.logwatch.Alert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * {@link SseAlertNotifier} 单元测试。
 *
 * <p>覆盖：无订阅者时不阻塞不抛异常、订阅者按序接收、持续流不完成。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class SseAlertNotifierTest {

    private static final int BUFFER = 8;

    private Alert alert(String fingerprint) {
        return Alert.create(fingerprint, "app.log", AlertLevel.ERROR, "ERROR", "摘要",
                "堆栈", 3, Instant.now());
    }

    @Test
    @DisplayName("无订阅者时 publish 非阻塞且不抛异常（尽力投递）")
    void should_notThrowOrBlock_when_noSubscriber() {
        SseAlertNotifier notifier = new SseAlertNotifier(BUFFER);

        assertThatCode(() -> {
            for (int i = 0; i < BUFFER * 4; i++) {
                notifier.publish(alert("fp-" + i));
            }
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("订阅者按发布顺序接收告警")
    void should_deliverInOrder_when_subscribed() {
        SseAlertNotifier notifier = new SseAlertNotifier(BUFFER);

        StepVerifier.create(notifier.stream().take(2))
                .then(() -> {
                    notifier.publish(alert("fp-1"));
                    notifier.publish(alert("fp-2"));
                })
                .expectNextMatches(a -> a.fingerprint().equals("fp-1"))
                .expectNextMatches(a -> a.fingerprint().equals("fp-2"))
                .verifyComplete();
    }

    @Test
    @DisplayName("流为持续流，发布后不会自动完成")
    void should_stayOpen_when_alertPublished() {
        SseAlertNotifier notifier = new SseAlertNotifier(BUFFER);

        StepVerifier.create(notifier.stream())
                .then(() -> notifier.publish(alert("fp-1")))
                .expectNextCount(1)
                .thenAwait(Duration.ofMillis(200))
                .thenCancel()
                .verify();
    }
}
