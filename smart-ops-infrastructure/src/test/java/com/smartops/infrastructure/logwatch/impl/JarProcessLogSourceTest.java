package com.smartops.infrastructure.logwatch.impl;

import com.smartops.domain.logwatch.LogEvent;
import com.smartops.infrastructure.logwatch.ProcessLocator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link JarProcessLogSource} 单元测试。
 *
 * <p>ProcessLocator 以 stub 注入，覆盖：立即定位、延迟定位重试、
 * 未定位时 stop、stop 幂等。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class JarProcessLogSourceTest {

    private static final Duration FAST = Duration.ofMillis(50);
    private static final String JAR = "/opt/apps/order-service.jar";

    @TempDir
    Path tempDir;

    private Path logFile;
    private Path stateDir;
    private List<LogEvent> events;
    private CountDownLatch latch;
    private JarProcessLogSource source;

    @BeforeEach
    void setUp() throws IOException {
        logFile = tempDir.resolve("order-service.log");
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

    private void append(String text) throws IOException {
        Files.writeString(logFile, text,
                Files.exists(logFile) ? StandardOpenOption.APPEND : StandardOpenOption.CREATE);
    }

    @Test
    @DisplayName("进程日志立即定位成功时开始 tail 采集")
    void should_tailLogFile_when_locatedImmediately() throws Exception {
        latch = new CountDownLatch(1);
        ProcessLocator locator = jar -> Optional.of(logFile);
        source = new JarProcessLogSource(JAR, locator, stateDir, FAST, FAST, e -> {
            events.add(e);
            latch.countDown();
        });
        source.start();

        append("2026-07-22 10:00:00 INFO order service up\n");

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(events.get(0).source()).isEqualTo(JAR);
        assertThat(events.get(0).content()).contains("order service up");
    }

    @Test
    @DisplayName("首次定位失败时按周期重试，定位成功后才采集")
    void should_retryUntilLocated_when_firstAttemptMisses() throws Exception {
        latch = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        ProcessLocator locator = jar ->
                attempts.incrementAndGet() < 3 ? Optional.empty() : Optional.of(logFile);
        source = new JarProcessLogSource(JAR, locator, stateDir, FAST, FAST, e -> {
            events.add(e);
            latch.countDown();
        });
        source.start();

        // 前两次定位失败期间不产生事件
        Thread.sleep(200);
        append("2026-07-22 10:00:00 INFO after retry\n");

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(attempts.get()).isGreaterThanOrEqualTo(3);
        assertThat(events.get(0).content()).contains("after retry");
    }

    @Test
    @DisplayName("始终定位不到时 stop 不抛异常、不写状态")
    void should_stopCleanly_when_neverLocated() {
        source = new JarProcessLogSource(JAR, jar -> Optional.empty(),
                stateDir, FAST, FAST, e -> events.add(e));
        source.start();

        source.stop();

        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("stop 幂等")
    void should_beIdempotent_when_stopCalledTwice() {
        source = new JarProcessLogSource(JAR, jar -> Optional.of(logFile),
                stateDir, FAST, FAST, e -> events.add(e));
        source.start();

        source.stop();
        source.stop();
    }

    @Test
    @DisplayName("start 幂等，重复调用不重复定位")
    void should_beIdempotent_when_startCalledTwice() {
        AtomicInteger attempts = new AtomicInteger();
        ProcessLocator locator = jar -> {
            attempts.incrementAndGet();
            return Optional.of(logFile);
        };
        source = new JarProcessLogSource(JAR, locator, stateDir, FAST, FAST, e -> events.add(e));

        source.start();
        source.start();

        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("name 返回 jar 包路径")
    void should_returnJarPath_when_nameCalled() {
        source = new JarProcessLogSource(JAR, jar -> Optional.empty(),
                stateDir, FAST, FAST, e -> events.add(e));

        assertThat(source.name()).isEqualTo(JAR);
    }
}
