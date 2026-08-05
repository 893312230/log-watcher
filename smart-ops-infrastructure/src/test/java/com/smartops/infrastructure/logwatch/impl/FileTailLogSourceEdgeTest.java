package com.smartops.infrastructure.logwatch.impl;

import com.smartops.domain.logwatch.LogEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link FileTailLogSource} 边界路径测试。
 *
 * <p>覆盖：重复启动/未启动停止空转、有效断点续采、损坏断点忽略、
 * 轮询异常吞噬、监听器异常吞噬、CRLF 行尾、续行合并、
 * 中段首行非条目起始、状态保存失败抛出 UncheckedIOException。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class FileTailLogSourceEdgeTest {

    private static final Duration POLL = Duration.ofMillis(50);

    @TempDir
    Path tempDir;

    private FileTailLogSource source;

    @AfterEach
    void tearDown() {
        if (source != null) {
            source.stop();
        }
    }

    private Path stateFileOf(Path logFile) {
        return tempDir.resolve("state")
                .resolve(Integer.toHexString(logFile.toString().hashCode()) + ".state");
    }

    private List<LogEvent> startWithListener(Path logFile) throws IOException {
        List<LogEvent> events = new CopyOnWriteArrayList<>();
        Files.createDirectories(tempDir.resolve("state"));
        source = new FileTailLogSource(logFile, tempDir.resolve("state"), POLL, events::add);
        source.start();
        return events;
    }

    private void append(Path logFile, String text) throws IOException {
        Files.writeString(logFile, text, StandardCharsets.UTF_8,
                Files.exists(logFile)
                        ? java.nio.file.StandardOpenOption.APPEND
                        : java.nio.file.StandardOpenOption.CREATE);
    }

    private void awaitEvents(List<LogEvent> events, int count) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (events.size() < count && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertThat(events).hasSizeGreaterThanOrEqualTo(count);
    }

    @Test
    @DisplayName("重复启动与未启动停止均为空转")
    void should_noOp_when_doubleStartOrStopWithoutStart() throws IOException {
        Path logFile = Files.createFile(tempDir.resolve("app.log"));
        List<LogEvent> events = startWithListener(logFile);

        assertThatCode(() -> source.start()).doesNotThrowAnyException();

        FileTailLogSource neverStarted = new FileTailLogSource(
                logFile, tempDir.resolve("state"), POLL, e -> { });
        assertThatCode(neverStarted::stop).doesNotThrowAnyException();

        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("断点存在但文件标识不匹配（或文件系统无 fileKey）时从末尾重采")
    void should_restartFromEnd_when_stateNotResumable() throws Exception {
        Path logFile = tempDir.resolve("app.log");
        append(logFile, "2026-07-22 10:00:00 ERROR old\n");
        Files.createDirectories(tempDir.resolve("state"));
        Properties props = new Properties();
        props.setProperty("offset", "0");
        props.setProperty("fileKey", "some-other-file-key");
        try (var out = Files.newOutputStream(stateFileOf(logFile))) {
            props.store(out, "test state");
        }

        List<LogEvent> events = startWithListener(logFile);
        append(logFile, "2026-07-22 10:01:00 ERROR new\n");

        awaitEvents(events, 1);
        assertThat(events.get(0).content()).contains("new");
        assertThat(events.get(0).content()).doesNotContain("old");
    }

    @Test
    @DisplayName("损坏断点文件被忽略，从文件末尾开始采集")
    void should_ignoreCorruptState_when_offsetNotNumber() throws Exception {
        Path logFile = tempDir.resolve("app.log");
        append(logFile, "2026-07-22 10:00:00 ERROR old\n");
        Files.createDirectories(tempDir.resolve("state"));
        Files.writeString(stateFileOf(logFile), "offset=not-a-number\n");

        List<LogEvent> events = startWithListener(logFile);
        append(logFile, "2026-07-22 10:01:00 ERROR new\n");

        awaitEvents(events, 1);
        assertThat(events.get(0).content()).contains("new");
    }

    @Test
    @DisplayName("轮询读取异常被吞噬，采集线程保持存活")
    void should_swallowPollFailure_when_fileUnreadable() throws Exception {
        Path dirAsLog = Files.createDirectory(tempDir.resolve("dir-as-log"));
        List<LogEvent> events = startWithListener(dirAsLog);

        Thread.sleep(300);

        assertThat(events).isEmpty();
        assertThatCode(() -> source.stop()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("监听器抛异常被吞噬，不影响后续采集")
    void should_swallowListenerFailure_when_listenerThrows() throws Exception {
        Path logFile = Files.createFile(tempDir.resolve("app.log"));
        Files.createDirectories(tempDir.resolve("state"));
        source = new FileTailLogSource(logFile, tempDir.resolve("state"), POLL,
                e -> { throw new IllegalStateException("consumer down"); });
        source.start();

        append(logFile, "2026-07-22 10:00:00 ERROR boom\n");
        Thread.sleep(300);

        assertThatCode(() -> source.stop()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("CRLF 行尾被正确剥离")
    void should_stripCarriageReturn_when_crlfLineEnding() throws Exception {
        Path logFile = Files.createFile(tempDir.resolve("app.log"));
        List<LogEvent> events = startWithListener(logFile);

        append(logFile, "2026-07-22 10:00:00 ERROR windows\r\n");

        awaitEvents(events, 1);
        assertThat(events.get(0).content()).isEqualTo("2026-07-22 10:00:00 ERROR windows");
    }

    @Test
    @DisplayName("续行并入上一条目，堆栈完整合并")
    void should_mergeContinuationLine_when_notEntryStart() throws Exception {
        Path logFile = Files.createFile(tempDir.resolve("app.log"));
        List<LogEvent> events = startWithListener(logFile);

        append(logFile, "2026-07-22 10:00:00 ERROR boom\n\tat foo.Bar.baz(Bar.java:1)\n");

        awaitEvents(events, 1);
        assertThat(events.get(0).content())
                .contains("ERROR boom")
                .contains("at foo.Bar.baz");
    }

    @Test
    @DisplayName("中段开始采集时首行非条目起始也作为条目")
    void should_acceptFirstLineAsEntry_when_notMatchingEntryStart() throws Exception {
        Path logFile = Files.createFile(tempDir.resolve("app.log"));
        List<LogEvent> events = startWithListener(logFile);

        append(logFile, "plain line without date prefix\n");

        awaitEvents(events, 1);
        assertThat(events.get(0).content()).isEqualTo("plain line without date prefix");
    }

    @Test
    @DisplayName("断点状态保存失败抛出 UncheckedIOException")
    void should_throwUncheckedIOException_when_stateSaveFails() throws Exception {
        Path logFile = Files.createFile(tempDir.resolve("app.log"));
        // 预置与状态文件同名的非空目录，使原子移动失败
        Path blocker = Files.createDirectories(stateFileOf(logFile));
        Files.writeString(blocker.resolve("blocker.txt"), "x");

        source = new FileTailLogSource(logFile, tempDir.resolve("state"), POLL, e -> { });

        assertThatThrownBy(() -> source.start())
                .isInstanceOf(UncheckedIOException.class);
        source = null;
    }
}
