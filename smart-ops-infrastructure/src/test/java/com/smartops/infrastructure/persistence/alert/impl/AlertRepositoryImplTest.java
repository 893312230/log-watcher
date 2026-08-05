package com.smartops.infrastructure.persistence.alert.impl;

import com.smartops.common.enums.AlertLevel;
import com.smartops.common.enums.AlertStatus;
import com.smartops.domain.logwatch.Alert;
import com.smartops.domain.logwatch.AlertPage;
import com.smartops.domain.logwatch.AlertQuery;
import com.smartops.infrastructure.persistence.alert.AlertRecordJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AlertRepositoryImpl} 数据层集成测试（@DataJpaTest + H2）。
 *
 * <p>覆盖：保存往返、多条件过滤分页、状态更新、缺失告警异常。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@DataJpaTest
class AlertRepositoryImplTest {

    private static final Instant T1 = Instant.parse("2026-07-22T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-07-22T11:00:00Z");

    @Autowired
    private AlertRecordJpaRepository jpaRepository;

    private AlertRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new AlertRepositoryImpl(jpaRepository);
    }

    private Alert newAlert(String fingerprint, AlertLevel level, String source, Instant time) {
        return Alert.create(fingerprint, source, level, "ERROR", "测试告警 " + fingerprint,
                "堆栈内容", 3, time)
                .withAnalysis("分析 " + fingerprint, "建议 " + fingerprint, time);
    }

    @Test
    @DisplayName("保存告警后分配 id，字段完整往返")
    void should_roundTripAllFields_when_saved() {
        Alert saved = repository.save(newAlert("fp1", AlertLevel.ERROR, "app.log", T1));

        assertThat(saved.id()).isNotNull();
        Optional<Alert> found = repository.findById(saved.id());
        assertThat(found).isPresent();
        assertThat(found.get().fingerprint()).isEqualTo("fp1");
        assertThat(found.get().level()).isEqualTo(AlertLevel.ERROR);
        assertThat(found.get().source()).isEqualTo("app.log");
        assertThat(found.get().stackTrace()).isEqualTo("堆栈内容");
        assertThat(found.get().analysis()).isEqualTo("分析 fp1");
        assertThat(found.get().suggestion()).isEqualTo("建议 fp1");
        assertThat(found.get().layerReached()).isEqualTo(3);
        assertThat(found.get().status()).isEqualTo(AlertStatus.OPEN);
        assertThat(found.get().occurrence()).isEqualTo(1);
    }

    @Test
    @DisplayName("findById 对不存在的 id 返回 empty")
    void should_returnEmpty_when_idMissing() {
        assertThat(repository.findById(99999L)).isEmpty();
    }

    @Test
    @DisplayName("按级别与来源过滤，按创建时间倒序分页")
    void should_filterAndPage_when_querying() {
        repository.save(newAlert("fp-e1", AlertLevel.ERROR, "app.log", T1));
        repository.save(newAlert("fp-e2", AlertLevel.ERROR, "app.log", T2));
        repository.save(newAlert("fp-w1", AlertLevel.WARN, "app.log", T2));
        repository.save(newAlert("fp-e3", AlertLevel.ERROR, "other.log", T2));

        AlertPage errors = repository.query(
                new AlertQuery(AlertLevel.ERROR, null, null, null, null, 0, 10));
        assertThat(errors.total()).isEqualTo(3);
        // 倒序：最新的在前
        assertThat(errors.items()).extracting(Alert::fingerprint)
                .containsExactly("fp-e2", "fp-e3", "fp-e1");

        AlertPage appErrors = repository.query(
                new AlertQuery(AlertLevel.ERROR, "app.log", null, null, null, 0, 10));
        assertThat(appErrors.items()).extracting(Alert::fingerprint)
                .containsExactly("fp-e2", "fp-e1");
    }

    @Test
    @DisplayName("按时间范围过滤")
    void should_filterByTimeRange_when_fromToGiven() {
        repository.save(newAlert("fp-old", AlertLevel.ERROR, "app.log", T1));
        repository.save(newAlert("fp-new", AlertLevel.ERROR, "app.log", T2));

        AlertPage page = repository.query(new AlertQuery(
                null, null, null, Instant.parse("2026-07-22T10:30:00Z"), null, 0, 10));

        assertThat(page.items()).extracting(Alert::fingerprint).containsExactly("fp-new");
    }

    @Test
    @DisplayName("按时间上界过滤")
    void should_filterByUpperBound_when_toGiven() {
        repository.save(newAlert("fp-old", AlertLevel.ERROR, "app.log", T1));
        repository.save(newAlert("fp-new", AlertLevel.ERROR, "app.log", T2));

        AlertPage page = repository.query(new AlertQuery(
                null, null, null, null, Instant.parse("2026-07-22T10:30:00Z"), 0, 10));

        assertThat(page.items()).extracting(Alert::fingerprint).containsExactly("fp-old");
    }

    @Test
    @DisplayName("分页元信息正确返回")
    void should_returnPageMeta_when_pageThroughResults() {
        for (int i = 0; i < 5; i++) {
            repository.save(newAlert("fp-" + i, AlertLevel.WARN, "app.log",
                    T1.plusSeconds(i * 60L)));
        }

        AlertPage page2 = repository.query(new AlertQuery(null, null, null, null, null, 1, 2));

        assertThat(page2.total()).isEqualTo(5);
        assertThat(page2.items()).hasSize(2);
        assertThat(page2.page()).isEqualTo(1);
        assertThat(page2.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("来源按包含匹配过滤（部分串命中）")
    void should_filterBySourceContains_when_partialGiven() {
        repository.save(newAlert("fp-s1", AlertLevel.ERROR, "/var/log/app.log", T1));
        repository.save(newAlert("fp-s2", AlertLevel.ERROR, "/var/log/other.log", T2));

        AlertPage page = repository.query(new AlertQuery(null, "app.log", null, null, null, 0, 10));

        assertThat(page.items()).extracting(Alert::fingerprint).containsExactly("fp-s1");
    }

    @Test
    @DisplayName("关键字按包含匹配过滤")
    void should_filterByKeywordContains_when_keywordGiven() {
        repository.save(Alert.create("fp-k1", "app.log", AlertLevel.ERROR, "OutOfMemoryError",
                "m1", "", 3, T1));
        repository.save(Alert.create("fp-k2", "app.log", AlertLevel.ERROR, "DiskFull",
                "m2", "", 3, T2));

        AlertPage page = repository.query(new AlertQuery(null, null, "OutOfMemory", null, null, 0, 10));

        assertThat(page.items()).extracting(Alert::fingerprint).containsExactly("fp-k1");
    }

    @Test
    @DisplayName("LIKE 通配符按字面处理，不作为模式字符")
    void should_escapeLikeWildcard_when_filtering() {
        repository.save(newAlert("fp-w1", AlertLevel.ERROR, "app.log", T1));

        AlertPage bySource = repository.query(new AlertQuery(null, "%", null, null, null, 0, 10));
        AlertPage byKeyword = repository.query(new AlertQuery(null, null, "_", null, null, 0, 10));

        assertThat(bySource.total()).isZero();
        assertThat(byKeyword.total()).isZero();
    }

    @Test
    @DisplayName("空白过滤串视为不过滤")
    void should_ignoreBlankFilter_when_sourceOrKeywordBlank() {
        repository.save(newAlert("fp-b1", AlertLevel.ERROR, "app.log", T1));

        AlertPage page = repository.query(new AlertQuery(null, "  ", "", null, null, 0, 10));

        assertThat(page.total()).isEqualTo(1);
    }

    @Test
    @DisplayName("updateStatus 更新状态与更新时间")
    void should_updateStatus_when_alertExists() {
        Alert saved = repository.save(newAlert("fp-ack", AlertLevel.ERROR, "app.log", T1));

        repository.updateStatus(saved.id(), AlertStatus.ACKED);

        Alert reloaded = repository.findById(saved.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(AlertStatus.ACKED);
        assertThat(reloaded.updatedAt()).isAfterOrEqualTo(T1);
    }

    @Test
    @DisplayName("updateStatus 对不存在的告警返回 empty")
    void should_returnEmpty_when_updateStatusOnMissing() {
        assertThat(repository.updateStatus(99999L, AlertStatus.RESOLVED)).isEmpty();
    }

    @Test
    @DisplayName("countByDay 按天聚合并排除起始时间之前的数据")
    void should_countByDay_when_called() {
        repository.save(newAlert("fp-d1", AlertLevel.ERROR, "app.log", T1));
        repository.save(newAlert("fp-d2", AlertLevel.ERROR, "app.log", T1.plusSeconds(3600)));
        repository.save(newAlert("fp-d3", AlertLevel.WARN, "app.log", T1.plusSeconds(86400)));

        Map<java.time.LocalDate, Long> counts = repository.countByDay(T1);

        assertThat(counts).hasSize(2);
        assertThat(counts.get(java.time.LocalDate.of(2026, 7, 22))).isEqualTo(2L);
        assertThat(counts.get(java.time.LocalDate.of(2026, 7, 23))).isEqualTo(1L);

        Map<java.time.LocalDate, Long> empty = repository.countByDay(T1.plusSeconds(86400 * 2L));
        assertThat(empty).isEmpty();
    }
}
