package com.smartops.domain.logwatch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link LogEvent} 单元测试。
 *
 * <p>验证日志事件的必填校验与指纹归一化逻辑：
 * 仅动态部分（数字/时间戳/IP/UUID）不同的同类日志必须产生相同指纹，
 * 这是 L0 抑制层去重合并的基础。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class LogEventTest {

    private static final Instant NOW = Instant.parse("2026-07-22T10:00:00Z");

    @Test
    @DisplayName("必填字段为 null 时构造失败")
    void should_throw_when_requiredFieldNull() {
        assertThatThrownBy(() -> new LogEvent(null, "content", NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LogEvent("app.log", null, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LogEvent("app.log", "content", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("仅数字不同的两条日志指纹相同")
    void should_sameFingerprint_when_onlyNumbersDiffer() {
        LogEvent e1 = new LogEvent("app.log", "Connection timeout after 3001 ms", NOW);
        LogEvent e2 = new LogEvent("app.log", "Connection timeout after 9876 ms", NOW);

        assertThat(e1.fingerprint()).isEqualTo(e2.fingerprint());
    }

    @Test
    @DisplayName("仅时间戳与 IP 不同的两条日志指纹相同")
    void should_sameFingerprint_when_onlyTimestampAndIpDiffer() {
        LogEvent e1 = new LogEvent("app.log",
                "2026-07-22 10:00:01 ERROR request from 192.168.1.10 failed", NOW);
        LogEvent e2 = new LogEvent("app.log",
                "2026-07-22 18:30:59 ERROR request from 10.0.0.255 failed", NOW);

        assertThat(e1.fingerprint()).isEqualTo(e2.fingerprint());
    }

    @Test
    @DisplayName("仅 UUID 不同的两条日志指纹相同")
    void should_sameFingerprint_when_onlyUuidDiffers() {
        LogEvent e1 = new LogEvent("app.log",
                "order 3f8c2a1e-1234-4abc-9def-0123456789ab create failed", NOW);
        LogEvent e2 = new LogEvent("app.log",
                "order 9b7d5e3f-9876-4def-8abc-fedcba987654 create failed", NOW);

        assertThat(e1.fingerprint()).isEqualTo(e2.fingerprint());
    }

    @Test
    @DisplayName("内容不同的两条日志指纹不同")
    void should_differentFingerprint_when_contentDiffers() {
        LogEvent e1 = new LogEvent("app.log", "Connection timeout", NOW);
        LogEvent e2 = new LogEvent("app.log", "NullPointerException at OrderService", NOW);

        assertThat(e1.fingerprint()).isNotEqualTo(e2.fingerprint());
    }

    @Test
    @DisplayName("指纹为 64 位十六进制字符串（SHA-256）")
    void should_return64HexChars_when_fingerprintCalled() {
        LogEvent event = new LogEvent("app.log", "any message", NOW);

        assertThat(event.fingerprint()).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("超长正文截断后参与哈希：前 200 字符相同则指纹相同")
    void should_truncateLongContent_when_fingerprintCalled() {
        String prefix = "x".repeat(300);
        LogEvent e1 = new LogEvent("app.log", prefix + "TAIL_AAA", NOW);
        LogEvent e2 = new LogEvent("app.log", prefix + "TAIL_BBB", NOW);

        assertThat(e1.fingerprint()).isEqualTo(e2.fingerprint());
    }
}
