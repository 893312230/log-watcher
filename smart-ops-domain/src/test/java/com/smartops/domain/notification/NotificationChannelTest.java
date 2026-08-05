package com.smartops.domain.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link NotificationChannel} 单元测试。
 *
 * @author smartops
 * @since 1.0.0
 */
class NotificationChannelTest {

    @Test
    @DisplayName("类型缺省时取 WEBHOOK，显式值保持")
    void should_defaultType_when_blank() {
        Instant now = Instant.now();
        assertThat(new NotificationChannel(1L, "ch", null, "u", true, now, now).type())
                .isEqualTo("WEBHOOK");
        assertThat(new NotificationChannel(1L, "ch", " ", "u", true, now, now).type())
                .isEqualTo("WEBHOOK");
        assertThat(new NotificationChannel(1L, "ch", "SLACK", "u", true, now, now).type())
                .isEqualTo("SLACK");
    }

    @Test
    @DisplayName("名称为 null 时抛出异常")
    void should_throw_when_nameNull() {
        assertThatThrownBy(() -> new NotificationChannel(null, null, null, "u", true, null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
