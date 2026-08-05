package com.smartops.infrastructure.logwatch.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * {@link JarProcessLogSource} 边界路径测试。
 *
 * <p>覆盖：定位器抛异常按周期重试不中断、委托采集器启动失败
 * 被调度入口吞噬。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class JarProcessLogSourceEdgeTest {

    private static final Duration FAST = Duration.ofMillis(50);
    private static final String JAR = "/opt/apps/order-service.jar";

    @TempDir
    Path tempDir;

    private JarProcessLogSource source;

    @AfterEach
    void tearDown() {
        if (source != null) {
            try {
                source.stop();
            } catch (RuntimeException ignored) {
                // 委托采集器启动失败的用例中 stop 可能抛出状态保存异常
            }
        }
    }

    @Test
    @DisplayName("定位器抛 RuntimeException 时按周期重试，调度不中断")
    void should_keepRetrying_when_locatorThrows() throws Exception {
        source = new JarProcessLogSource(JAR,
                jar -> {
                    throw new IllegalStateException("jps blew up");
                },
                tempDir.resolve("state"), FAST, FAST, e -> { });

        source.start();
        Thread.sleep(300);

        assertThatCode(() -> source.stop()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("委托采集器启动失败被调度入口吞噬，重试继续")
    void should_swallowDelegateStartFailure_when_stateSaveFails() throws Exception {
        Path logFile = Files.createFile(tempDir.resolve("located.log"));
        Path stateDir = tempDir.resolve("state");
        Files.createDirectories(stateDir);
        // 预置与断点状态文件同名的非空目录，使 saveState 原子移动失败
        Path blocker = Files.createDirectories(stateDir.resolve(
                Integer.toHexString(logFile.toString().hashCode()) + ".state"));
        Files.writeString(blocker.resolve("blocker.txt"), "x");

        // 首次同步定位返回 empty（走调度重试），后续定位成功但委托启动失败
        AtomicInteger attempts = new AtomicInteger();
        source = new JarProcessLogSource(JAR,
                jar -> attempts.incrementAndGet() < 2
                        ? Optional.empty()
                        : Optional.of(logFile),
                stateDir, FAST, FAST, e -> { });

        source.start();
        Thread.sleep(300);

        assertThat(attempts.get()).isGreaterThanOrEqualTo(2);
        source = null;
    }
}
