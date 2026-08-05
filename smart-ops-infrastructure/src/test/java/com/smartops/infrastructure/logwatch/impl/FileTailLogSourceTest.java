package com.smartops.infrastructure.logwatch.impl;

import com.smartops.domain.logwatch.LogEvent;
import com.smartops.infrastructure.logwatch.LogEventListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FileTailLogSource} 单元测试。
 *
 * <p>覆盖：追加采集、多行堆栈合并、半行缓冲、文件截断重读、
 * 断点续采、初见跳历史、空闲冲刷、文件后创建。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class FileTailLogSourceTest {

    private static final Duration POLL = Duration.ofMillis(50);

    @TempDir
    Path tempDir;

    private Path logFile;
    private Path stateDir;
    private List<LogEvent> events;
    private CountDownLatch latch;
    private FileTailLogSource source;

    @BeforeEach
    void setUp() throws IOException {
        logFile = tempDir.resolve("app.log");
        stateDir = tempDir.resolve("state");
        Files.createDirectories(stateDir);
        events = new CopyOnWriteArrayList<>();
    }

    @AfterEach
    void tearDown() {
        if (source != null) {
            source.stop();
        }
    }

    /** 启动采集器，latch 计数达到 expect 时视为事件就绪。 */
    private void startSource(int expect) {
        latch = new CountDownLatch(expect);
        LogEventListener listener = e -> {
            events.add(e);
            latch.countDown();
        };
        source = new FileTailLogSource(logFile, stateDir, POLL, listener);
        source.start();
    }

    private void append(String text) throws IOException {
        Files.writeString(logFile, text, StandardCharsets.UTF_8,
                Files.exists(logFile)
                        ? java.nio.file.StandardOpenOption.APPEND
                        : java.nio.file.StandardOpenOption.CREATE);
    }

    @Test
    @DisplayName("启动后追加的日志行被采集为事件")
    void should_emitNewLines_when_appendedAfterStart() throws Exception {
        startSource(1);

        append("2026-07-22 10:00:00 INFO service started\n");

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).content()).isEqualTo("2026-07-22 10:00:00 INFO service started");
        assertThat(events.get(0).source()).isEqualTo(logFile.toString());
    }

    @Test
    @DisplayName("非条目起始行并入上一条，堆栈合并为单个事件")
    void should_mergeMultilineStackIntoSingleEvent_when_continuationLines() throws Exception {
        startSource(1);

        // 单次写入保证一个轮询周期内读全，消除时序干扰
        append("2026-07-22 10:00:00 ERROR boom\n"
                + "\tat com.x.Foo.bar(Foo.java:42)\n"
                + "\tat com.x.Main.main(Main.java:10)\n"
                + "2026-07-22 10:00:01 INFO recovered\n");

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(events.get(0).content()).isEqualTo(
                "2026-07-22 10:00:00 ERROR boom\n"
                        + "\tat com.x.Foo.bar(Foo.java:42)\n"
                        + "\tat com.x.Main.main(Main.java:10)");
    }

    @Test
    @DisplayName("无换行符的半行暂不产出，换行补齐后才产出")
    void should_notEmitPartialLineUntilNewline() throws Exception {
        startSource(1);

        append("2026-07-22 10:00:00 ERROR partial");
        Thread.sleep(300);
        assertThat(events).isEmpty();

        append(" line done\n");
        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(events.get(0).content()).isEqualTo("2026-07-22 10:00:00 ERROR partial line done");
    }

    @Test
    @DisplayName("空闲时缓冲中的条目被冲刷产出")
    void should_flushBufferedEntry_when_idle() throws Exception {
        startSource(1);

        append("2026-07-22 10:00:00 ERROR boom\n"
                + "\tat com.x.Foo.bar(Foo.java:42)\n");

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(events.get(0).content()).contains("at com.x.Foo.bar");
    }

    @Test
    @DisplayName("文件截断（copytruncate 轮替）后从头重读")
    void should_reReadFromStart_when_fileTruncated() throws Exception {
        startSource(1);

        append("2026-07-22 10:00:00 INFO first\n");
        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();

        // 模拟 copytruncate：清空文件；等待一个轮询周期让采集器观测到长度回退
        try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "rw")) {
            raf.setLength(0);
        }
        Thread.sleep(200);
        CountDownLatch second = new CountDownLatch(1);
        events.clear();
        latch = second;
        append("2026-07-22 10:01:00 INFO after-rotate\n");

        assertThat(second.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(events).anySatisfy(e ->
                assertThat(e.content()).contains("after-rotate"));
    }

    @Test
    @DisplayName("重启后从持久化 offset 续采，不重读历史")
    void should_resumeFromPersistedOffset_when_restarted() throws Exception {
        startSource(1);
        append("2026-07-22 10:00:00 INFO before-restart\n");
        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        source.stop();

        // 同一路径新实例：应续采而非重读
        CountDownLatch second = new CountDownLatch(1);
        List<LogEvent> secondEvents = new CopyOnWriteArrayList<>();
        source = new FileTailLogSource(logFile, stateDir, POLL, e -> {
            secondEvents.add(e);
            second.countDown();
        });
        source.start();
        append("2026-07-22 10:05:00 INFO after-restart\n");

        assertThat(second.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(secondEvents).allSatisfy(e ->
                assertThat(e.content()).doesNotContain("before-restart"));
        assertThat(secondEvents).anySatisfy(e ->
                assertThat(e.content()).contains("after-restart"));
    }

    @Test
    @DisplayName("初见文件跳过历史内容，只采集新增")
    void should_skipExistingContent_when_firstSight() throws Exception {
        append("2026-07-22 09:00:00 INFO ancient history\n");
        startSource(1);

        append("2026-07-22 10:00:00 INFO fresh line\n");

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(events).allSatisfy(e ->
                assertThat(e.content()).doesNotContain("ancient history"));
        assertThat(events).anySatisfy(e ->
                assertThat(e.content()).contains("fresh line"));
    }

    @Test
    @DisplayName("文件尚不存在时不报错，创建并追加后正常采集")
    void should_tolerateMissingFile_when_createdLater() throws Exception {
        startSource(1);
        Thread.sleep(200);

        append("2026-07-22 10:00:00 INFO late born\n");

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(events).anySatisfy(e ->
                assertThat(e.content()).contains("late born"));
    }

    @Test
    @DisplayName("stop 幂等，重复调用不抛异常")
    void should_beIdempotent_when_stopCalledTwice() {
        startSource(0);

        source.stop();
        source.stop();
    }
}
