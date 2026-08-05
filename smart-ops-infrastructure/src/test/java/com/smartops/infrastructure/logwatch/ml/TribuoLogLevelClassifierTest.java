package com.smartops.infrastructure.logwatch.ml;

import com.smartops.common.enums.AlertLevel;
import com.smartops.domain.logwatch.ClassificationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TribuoLogLevelClassifier} 单元测试。
 *
 * <p>固定 seed 训练小型 TSV，验证训练-评估-门禁-推理-降级全链路；
 * 不依赖 Spring 容器与外部文件。</p>
 */
class TribuoLogLevelClassifierTest {

    private static final long SEED = 7L;

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("良好数据训练后就绪且能正确分类正则漏判句式")
    void should_be_ready_and_classify_when_training_data_good() throws IOException {
        TribuoLogLevelClassifier classifier = new TribuoLogLevelClassifier(
                writeTsv(goodData()), 0.6, SEED);

        assertThat(classifier.isReady()).isTrue();
        assertThat(classifier.getHoldoutAccuracy()).isGreaterThanOrEqualTo(0.6);

        ClassificationResult error = classifier.classify("Connection refused by remote host 10.9.9.9");
        assertThat(error.level()).isEqualTo(AlertLevel.ERROR);
        assertThat(error.confidence()).isGreaterThan(0.5);

        ClassificationResult info = classifier.classify("User admin logged in successfully");
        assertThat(info.level()).isEqualTo(AlertLevel.INFO);
    }

    @Test
    @DisplayName("留出集准确率低于门禁时不就绪且推理弃权")
    void should_abstain_when_accuracy_below_gate() throws IOException {
        TribuoLogLevelClassifier classifier = new TribuoLogLevelClassifier(
                writeTsv(goodData()), 1.5, SEED);

        assertThat(classifier.isReady()).isFalse();
        assertThat(classifier.classify("Connection refused")).isEqualTo(ClassificationResult.abstain());
    }

    @Test
    @DisplayName("数据文件不存在时不抛异常且不就绪")
    void should_not_be_ready_when_data_file_missing() {
        TribuoLogLevelClassifier classifier = new TribuoLogLevelClassifier(
                new FileSystemResource(tempDir.resolve("nonexistent.tsv")), 0.6, SEED);

        assertThat(classifier.isReady()).isFalse();
        assertThat(classifier.classify("Connection refused")).isEqualTo(ClassificationResult.abstain());
    }

    @Test
    @DisplayName("全部为无效行的数据不就绪")
    void should_not_be_ready_when_data_garbage() {
        ByteArrayResource garbage = new ByteArrayResource(
                "# only comments\n\n???\nNO_TAB_LINE\n".getBytes(StandardCharsets.UTF_8));
        TribuoLogLevelClassifier classifier = new TribuoLogLevelClassifier(garbage, 0.6, SEED);

        assertThat(classifier.isReady()).isFalse();
    }

    @Test
    @DisplayName("某类样本不足时不就绪")
    void should_not_be_ready_when_class_samples_insufficient() {
        String data = "ERROR\tconnection refused by host\n"
                + "WARN\tdisk usage above 85 percent\n"
                + "WARN\tlatency p99 exceeds target\n"
                + "WARN\treplica lag growing beyond threshold\n"
                + "WARN\tcertificate expires in seven days\n"
                + "INFO\tapplication started on port 8080\n"
                + "INFO\thealth check passed for targets\n"
                + "INFO\tbackup completed successfully today\n"
                + "INFO\tcache warmup completed quickly now\n";
        TribuoLogLevelClassifier classifier = new TribuoLogLevelClassifier(
                new ByteArrayResource(data.getBytes(StandardCharsets.UTF_8)), 0.6, SEED);

        assertThat(classifier.isReady()).isFalse();
    }

    @Test
    @DisplayName("空白内容推理返回弃权")
    void should_abstain_on_blank_content() throws IOException {
        TribuoLogLevelClassifier classifier = new TribuoLogLevelClassifier(
                writeTsv(goodData()), 0.6, SEED);

        assertThat(classifier.classify("   ")).isEqualTo(ClassificationResult.abstain());
        assertThat(classifier.classify("")).isEqualTo(ClassificationResult.abstain());
    }

    @Test
    @DisplayName("内置种子数据留出集准确率达到 0.80 基线（种子质量回归门禁）")
    void should_meet_baseline_on_bundled_seed_data() {
        TribuoLogLevelClassifier classifier = new TribuoLogLevelClassifier(
                new ClassPathResource("ml/log-level-seed.tsv"), 0.8, 42L);

        assertThat(classifier.isReady()).isTrue();
        assertThat(classifier.getHoldoutAccuracy()).isGreaterThanOrEqualTo(0.8);
        assertThat(classifier.classify("Connection refused by db-01:3306").level())
                .isEqualTo(AlertLevel.ERROR);
        assertThat(classifier.classify("Out of memory: Kill process 2281 (java)").level())
                .isEqualTo(AlertLevel.ERROR);
    }

    @Test
    @DisplayName("未就绪时留出集准确率为 0")
    void should_report_zero_accuracy_when_never_trained() {
        TribuoLogLevelClassifier classifier = new TribuoLogLevelClassifier(
                new FileSystemResource(tempDir.resolve("missing.tsv")), 0.6, SEED);

        assertThat(classifier.getHoldoutAccuracy()).isZero();
    }

    private FileSystemResource writeTsv(String content) throws IOException {
        Path file = tempDir.resolve("train.tsv");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return new FileSystemResource(file);
    }

    /** 构造三类别各 12 条、同类共享词汇池的最小训练集（保证留出样本与训练特征有重叠）。 */
    private String goodData() {
        StringBuilder sb = new StringBuilder();
        String[] errors = {
                "connection refused by remote host", "request refused by upstream server",
                "connection timeout after long wait", "read timeout waiting for socket",
                "permission denied opening shared file", "access denied by security policy",
                "deadlock detected between two threads", "deadlock victim transaction rolled back",
                "data corruption found in block device", "corruption detected on storage page",
                "worker crashed with fatal signal", "process crashed dumping core file"};
        String[] warns = {
                "latency p99 exceeds target threshold", "latency spike detected on endpoint",
                "retry attempt for upstream call", "retry scheduled after transient failure",
                "replica lag approaching alert threshold", "replica lag growing beyond baseline",
                "heap usage above eighty percent", "memory usage climbing steadily today",
                "certificate expires in seven days", "certificate renewal reminder expires soon",
                "cache hit ratio dropped significantly", "cache miss rate rising sharply now"};
        String[] infos = {
                "application started on port successfully", "service started and ready now",
                "user logged in successfully today", "user logged out successfully tonight",
                "health check passed for all targets", "readiness probe passed for pod",
                "scheduled job finished successfully tonight", "batch job completed without issues",
                "configuration loaded from config store", "feature flags loaded at startup",
                "backup completed written to storage", "snapshot completed and uploaded successfully"};
        for (String e : errors) {
            sb.append("ERROR\t").append(e).append('\n');
        }
        for (String w : warns) {
            sb.append("WARN\t").append(w).append('\n');
        }
        for (String i : infos) {
            sb.append("INFO\t").append(i).append('\n');
        }
        return sb.toString();
    }
}
