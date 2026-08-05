package com.smartops.domain.logwatch;

import com.smartops.common.enums.AlertLevel;
import com.smartops.common.enums.AlertStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link Alert} 单元测试。
 *
 * <p>验证告警模型的创建工厂（默认 OPEN/单次发生）与必填校验。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class AlertTest {

    private static final Instant NOW = Instant.parse("2026-07-22T10:00:00Z");

    private Alert newAlert() {
        return Alert.create("fp-1", "app.log", AlertLevel.ERROR, "OutOfMemoryError",
                "java.lang.OutOfMemoryError: Java heap space", "at com.x.Foo.bar(Foo.java:1)", 3, NOW);
    }

    @Test
    @DisplayName("create 工厂默认 OPEN 状态、发生次数 1、无 id")
    void should_defaultOpenAndSingleOccurrence_when_created() {
        Alert alert = newAlert();

        assertThat(alert.id()).isNull();
        assertThat(alert.status()).isEqualTo(AlertStatus.OPEN);
        assertThat(alert.occurrence()).isEqualTo(1);
        assertThat(alert.createdAt()).isEqualTo(NOW);
        assertThat(alert.updatedAt()).isEqualTo(NOW);
        assertThat(alert.layerReached()).isEqualTo(3);
        assertThat(alert.analysis()).isEmpty();
        assertThat(alert.suggestion()).isEmpty();
    }

    @Test
    @DisplayName("必填字段为 null 时构造失败")
    void should_throw_when_requiredFieldNull() {
        assertThatThrownBy(() -> Alert.create(null, "app.log", AlertLevel.ERROR, "k", "m", "s", 1, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Alert.create("fp", null, AlertLevel.ERROR, "k", "m", "s", 1, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Alert.create("fp", "app.log", null, "k", "m", "s", 1, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Alert.create("fp", "app.log", AlertLevel.ERROR, "k", null, "s", 1, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Alert.create("fp", "app.log", AlertLevel.ERROR, "k", "m", "s", 1, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("create 工厂对 null 关键字与堆栈归一化为空串")
    void should_normalizeNullOptionalFields_when_created() {
        Alert alert = Alert.create("fp", "app.log", AlertLevel.ERROR, null, "m", null, 1, NOW);

        assertThat(alert.keyword()).isEmpty();
        assertThat(alert.stackTrace()).isEmpty();
    }

    @Test
    @DisplayName("withAnalysis 对 null 分析与建议归一化为空串")
    void should_normalizeNullAnalysis_when_withAnalysisCalled() {
        Alert alert = newAlert().withAnalysis(null, null, NOW);

        assertThat(alert.analysis()).isEmpty();
        assertThat(alert.suggestion()).isEmpty();
    }

    @Test
    @DisplayName("withId 返回带持久化 id 的副本，其余字段不变")
    void should_returnCopyWithId_when_withIdCalled() {
        Alert alert = newAlert().withId(42L);

        assertThat(alert.id()).isEqualTo(42L);
        assertThat(alert.fingerprint()).isEqualTo("fp-1");
        assertThat(alert.status()).isEqualTo(AlertStatus.OPEN);
    }

    @Test
    @DisplayName("withAnalysis 返回携带分析结论与建议的副本")
    void should_returnCopyWithAnalysis_when_withAnalysisCalled() {
        Alert alert = newAlert().withAnalysis("堆内存不足", "调大 -Xmx", NOW.plusSeconds(60));

        assertThat(alert.analysis()).isEqualTo("堆内存不足");
        assertThat(alert.suggestion()).isEqualTo("调大 -Xmx");
        assertThat(alert.updatedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(alert.createdAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("withOccurrence 返回合并次数更新后的副本")
    void should_returnCopyWithOccurrence_when_withOccurrenceCalled() {
        Alert alert = newAlert().withOccurrence(7, NOW.plusSeconds(30));

        assertThat(alert.occurrence()).isEqualTo(7);
        assertThat(alert.updatedAt()).isEqualTo(NOW.plusSeconds(30));
    }

    @Test
    @DisplayName("withStatus 返回状态流转后的副本")
    void should_returnCopyWithStatus_when_withStatusCalled() {
        Alert alert = newAlert().withStatus(AlertStatus.ACKED, NOW.plusSeconds(10));

        assertThat(alert.status()).isEqualTo(AlertStatus.ACKED);
        assertThat(alert.updatedAt()).isEqualTo(NOW.plusSeconds(10));
    }
}
