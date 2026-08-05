package com.smartops.infrastructure.logwatch.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link JpsProcessLocator} 边界路径测试。
 *
 * <p>覆盖：生产默认构造（真实 jps）、jps 非数字 pid 跳过、
 * jps 中断恢复中断标记、/proc 扫描跳过非数字目录与缺失 cmdline、
 * procRoot 不存在、默认 fd 解析器非符号链接/无 fd 目录、默认命令执行器。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class JpsProcessLocatorEdgeTest {

    private static final String JAR = "/opt/apps/order-service.jar";

    @TempDir
    Path procRoot;

    @Test
    @DisplayName("默认构造对不存在的 jar 返回 empty（真实 jps 或回退路径均不命中）")
    void should_returnEmpty_when_defaultLocatorAndJarMissing() {
        JpsProcessLocator locator = new JpsProcessLocator();

        assertThat(locator.locateLogFile("no-such-jar-xyz-12345.jar")).isEmpty();
    }

    @Test
    @DisplayName("jps 输出行首个 token 非数字时跳过")
    void should_skipLine_when_jpsPidNotNumeric() {
        JpsProcessLocator locator = new JpsProcessLocator(
                cmd -> List.of("abc " + JAR),
                procRoot,
                pid -> Optional.of(Path.of("/var/log/x.log")));

        assertThat(locator.locateLogFile(JAR)).isEmpty();
    }

    @Test
    @DisplayName("jps 执行被中断时恢复中断标记并回退")
    void should_restoreInterruptFlag_when_jpsInterrupted() {
        JpsProcessLocator locator = new JpsProcessLocator(
                cmd -> {
                    throw new InterruptedException("interrupted");
                },
                procRoot,
                pid -> Optional.empty());

        assertThat(locator.locateLogFile(JAR)).isEmpty();
        // 断言的同时清除标记，避免污染后续测试
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    @DisplayName("/proc 扫描跳过非数字目录与缺失 cmdline 的进程")
    void should_skipInvalidEntries_when_procScanning() throws IOException {
        Files.createDirectories(procRoot.resolve("not-a-pid"));
        Files.createDirectories(procRoot.resolve("5555"));
        Path hit = Files.createDirectories(procRoot.resolve("8888"));
        Files.writeString(hit.resolve("cmdline"), "java -jar " + JAR);

        JpsProcessLocator locator = new JpsProcessLocator(
                cmd -> {
                    throw new IOException("jps unavailable");
                },
                procRoot,
                pid -> Optional.of(Path.of("/var/log/apps/" + pid + ".log")));

        assertThat(locator.locateLogFile(JAR))
                .contains(Path.of("/var/log/apps/8888.log"));
    }

    @Test
    @DisplayName("procRoot 不存在时 /proc 扫描直接返回 empty")
    void should_returnEmpty_when_procRootMissing() {
        JpsProcessLocator locator = new JpsProcessLocator(
                cmd -> {
                    throw new IOException("jps unavailable");
                },
                procRoot.resolve("no-such-dir"),
                pid -> Optional.of(Path.of("/var/log/x.log")));

        assertThat(locator.locateLogFile(JAR)).isEmpty();
    }

    @Test
    @DisplayName("默认 fd 解析器：fd 非符号链接或不存在时返回 empty")
    void should_returnEmpty_when_fdNotSymlinkOrMissing() throws IOException {
        Path fdDir = Files.createDirectories(procRoot.resolve("1234").resolve("fd"));
        Files.writeString(fdDir.resolve("1"), "not a symlink");

        var resolver = JpsProcessLocator.defaultFdResolver(procRoot);

        assertThat(resolver.apply("1234")).isEmpty();
        assertThat(resolver.apply("9999")).isEmpty();
    }

    @Test
    @DisplayName("默认命令执行器真实执行 jps 并读取输出")
    void should_executeCommand_when_defaultExecutor() throws Exception {
        List<String> lines = JpsProcessLocator.defaultExecutor().exec("jps", "-l");

        assertThat(lines).isNotNull();
    }
}
