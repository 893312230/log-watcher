package com.smartops.infrastructure.logwatch.impl;

import com.smartops.infrastructure.logwatch.CommandExecutor;
import com.smartops.infrastructure.logwatch.ProcessLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 基于 jps + /proc 的进程日志定位器。
 *
 * <p>定位链路（三级降级）：</p>
 * <ol>
 *   <li>{@code jps -l} 匹配 jar 名取 pid（JDK 环境常规路径）</li>
 *   <li>jps 不可用/未命中时遍历 {@code /proc/&lt;pid&gt;/cmdline} 匹配 jar 名</li>
 *   <li>pid 命中后解析 {@code /proc/&lt;pid&gt;/fd/1}（stdout）符号链接得日志文件，
 *       非文件（管道/终端）再试 fd/2（stderr），均失败返回 empty</li>
 * </ol>
 *
 * <p>非 Linux 环境（无 /proc）所有路径均返回 empty，由调用方跳过该采集源。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public class JpsProcessLocator implements ProcessLocator {

    private static final Logger log = LoggerFactory.getLogger(JpsProcessLocator.class);

    /** 默认 /proc 根路径（Linux）。 */
    private static final Path DEFAULT_PROC_ROOT = Path.of("/proc");

    /** jps 命令超时秒数。 */
    private static final long JPS_TIMEOUT_SECONDS = 5;

    private final CommandExecutor executor;
    private final Path procRoot;
    private final Function<String, Optional<Path>> fdResolver;

    /**
     * 构造生产用定位器（真实 jps、/proc、fd 符号链接解析）。
     */
    public JpsProcessLocator() {
        this(defaultExecutor(), DEFAULT_PROC_ROOT, defaultFdResolver(DEFAULT_PROC_ROOT));
    }

    /**
     * 构造可测试定位器。
     *
     * @param executor   命令执行器
     * @param procRoot   /proc 根路径
     * @param fdResolver pid → stdout 日志路径解析函数
     */
    public JpsProcessLocator(CommandExecutor executor, Path procRoot,
                             Function<String, Optional<Path>> fdResolver) {
        this.executor = executor;
        this.procRoot = procRoot;
        this.fdResolver = fdResolver;
    }

    @Override
    public Optional<Path> locateLogFile(String jarPath) {
        String jarName = Path.of(jarPath).getFileName().toString();
        Optional<String> pid = findPidByJps(jarName);
        if (pid.isEmpty()) {
            pid = findPidByProcScan(jarName);
        }
        pid.ifPresent(p -> log.debug("jar {} 定位到 pid {}", jarName, p));
        return pid.flatMap(fdResolver);
    }

    /**
     * 经 {@code jps -l} 查找 pid：输出格式 {@code <pid> <主类或jar路径>}。
     */
    private Optional<String> findPidByJps(String jarName) {
        try {
            List<String> lines = executor.exec("jps", "-l");
            for (String line : lines) {
                if (line.contains(jarName)) {
                    String pid = line.trim().split("\\s+")[0];
                    if (pid.matches("\\d+")) {
                        return Optional.of(pid);
                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.debug("jps 不可用，回退 /proc 扫描: {}", e.toString());
        }
        return Optional.empty();
    }

    /**
     * 遍历 /proc/&lt;pid&gt;/cmdline 查找 pid（jps 不可用时的降级路径）。
     */
    private Optional<String> findPidByProcScan(String jarName) {
        if (!Files.isDirectory(procRoot)) {
            return Optional.empty();
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(procRoot)) {
            for (Path entry : stream) {
                String dirName = entry.getFileName().toString();
                if (!dirName.matches("\\d+")) {
                    continue;
                }
                Path cmdline = entry.resolve("cmdline");
                if (!Files.isRegularFile(cmdline)) {
                    continue;
                }
                try {
                    if (Files.readString(cmdline, StandardCharsets.UTF_8).contains(jarName)) {
                        return Optional.of(dirName);
                    }
                } catch (IOException e) {
                    // 进程可能刚退出，跳过
                    log.debug("读取 cmdline 失败 {}: {}", cmdline, e.toString());
                }
            }
        } catch (IOException e) {
            log.debug("遍历 /proc 失败: {}", e.toString());
        }
        return Optional.empty();
    }

    /**
     * 生产默认 fd 解析：读 /proc/&lt;pid&gt;/fd/1（stdout）符号链接，
     * 指向常规文件则返回；否则试 fd/2（stderr）。
     *
     * @param procRoot /proc 根路径
     * @return pid → 日志路径解析函数
     */
    public static Function<String, Optional<Path>> defaultFdResolver(Path procRoot) {
        return pid -> {
            for (String fd : new String[]{"1", "2"}) {
                Path fdLink = procRoot.resolve(pid).resolve("fd").resolve(fd);
                try {
                    if (!Files.isSymbolicLink(fdLink)) {
                        continue;
                    }
                    Path target = fdLink.toAbsolutePath().getParent()
                            .resolve(Files.readSymbolicLink(fdLink)).normalize();
                    if (Files.isRegularFile(target)) {
                        return Optional.of(target);
                    }
                } catch (IOException e) {
                    log.debug("解析 fd 失败 {}: {}", fdLink, e.toString());
                }
            }
            return Optional.empty();
        };
    }

    /**
     * 生产默认命令执行器：ProcessBuilder 执行并限时等待。
     *
     * @return 命令执行器
     */
    public static CommandExecutor defaultExecutor() {
        return command -> {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            List<String> lines;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                lines = reader.lines().toList();
            }
            if (!process.waitFor(JPS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("命令执行超时: " + String.join(" ", command));
            }
            return lines;
        };
    }
}
