package com.smartops.agent.logwatch;

import com.smartops.agent.logwatch.pipeline.impl.L1ClassifyLayer;
import com.smartops.common.enums.AlertLevel;
import com.smartops.common.enums.AlertStatus;
import com.smartops.domain.logwatch.Alert;
import com.smartops.domain.logwatch.AlertPage;
import com.smartops.domain.logwatch.AlertQuery;
import com.smartops.domain.logwatch.port.AlertRepository;
import com.smartops.infrastructure.logwatch.impl.FileTailLogSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * logwatch 采集到落库全链路集成测试。
 *
 * <p>真实 FileTailLogSource + AlertPipelineService（L1 定级层），
 * 桩化 AlertRepository 验证：文件追加 ERROR 行 → 关键字命中 →
 * 定级 → 落库 → 通知。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class LogWatchPipelineIntegrationTest {

    @TempDir
    private Path tempDir;

    @Test
    @DisplayName("文件追加 ERROR 行后完成采集-分析-落库-通知全链路")
    void should_persistAlert_when_errorLineAppended() throws Exception {
        Path logFile = Files.createFile(tempDir.resolve("app.log"));
        Path stateDir = tempDir.resolve("state");
        InMemoryAlertRepository repository = new InMemoryAlertRepository();
        List<Alert> published = new CopyOnWriteArrayList<>();

        AlertPipelineService pipeline = new AlertPipelineService(
                new LogKeywordMatcher(List.of()),
                List.of(new L1ClassifyLayer()),
                repository, published::add, null, null, 16);
        FileTailLogSource source = new FileTailLogSource(
                logFile, stateDir, Duration.ofMillis(50), pipeline::onEvent);

        pipeline.start();
        source.start();
        try {
            Files.writeString(logFile,
                    "2026-07-22 10:00:00.000 ERROR  c.e.App - boom happened\n",
                    StandardOpenOption.APPEND);

            await().atMost(Duration.ofSeconds(5))
                    .until(() -> repository.savedCount() == 1);

            Alert alert = repository.first();
            assertThat(alert.level()).isEqualTo(AlertLevel.ERROR);
            assertThat(alert.message()).contains("boom happened");
            assertThat(alert.source()).isEqualTo(logFile.toString());
            assertThat(alert.fingerprint()).hasSize(64);
            assertThat(published).hasSize(1);
        } finally {
            source.stop();
            pipeline.stop();
        }
    }

    /**
     * 内存版告警持久化桩。
     */
    private static final class InMemoryAlertRepository implements AlertRepository {

        private final List<Alert> saved = new CopyOnWriteArrayList<>();
        private final AtomicLong ids = new AtomicLong();

        @Override
        public Alert save(Alert alert) {
            Alert stored = alert.id() == null ? alert.withId(ids.incrementAndGet()) : alert;
            saved.add(stored);
            return stored;
        }

        @Override
        public Optional<Alert> findById(long id) {
            return saved.stream().filter(a -> a.id() != null && a.id() == id).findFirst();
        }

        @Override
        public AlertPage query(AlertQuery query) {
            return new AlertPage(saved, saved.size(), query.page(), query.size());
        }

        @Override
        public Optional<Alert> updateStatus(long id, AlertStatus status) {
            return findById(id);
        }

        @Override
        public java.util.Map<java.time.LocalDate, Long> countByDay(java.time.Instant since) {
            return java.util.Map.of();
        }

        int savedCount() {
            return saved.size();
        }

        Alert first() {
            return saved.get(0);
        }
    }
}
