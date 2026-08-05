package com.smartops.infrastructure.logwatch.impl;

import com.smartops.domain.logwatch.LogEvent;
import com.smartops.infrastructure.logwatch.LogEventListener;
import com.smartops.infrastructure.logwatch.LogSource;
import com.smartops.infrastructure.logwatch.ProcessLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * jar 进程日志采集源。
 *
 * <p>面向"指定运行中的 jar 包路径"场景：经 {@link ProcessLocator}
 * 定位进程 stdout 日志文件后委托 {@link FileTailLogSource} 采集；
 * 定位失败按周期重试（进程可能尚未启动），永不抛出。
 * 产出事件的 source 统一改写为 jar 包路径，屏蔽底层日志文件细节。</p>
 *
 * <p>线程约束：单线程调度定位重试；{@link #start()}/{@link #stop()} 幂等。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public class JarProcessLogSource implements LogSource {

    private static final Logger log = LoggerFactory.getLogger(JarProcessLogSource.class);

    private final String jarPath;
    private final ProcessLocator locator;
    private final Path stateDir;
    private final Duration pollInterval;
    private final Duration locateRetryInterval;
    private final LogEventListener listener;
    private final ScheduledExecutorService executor;

    private FileTailLogSource delegate;
    private ScheduledFuture<?> locateFuture;
    private volatile boolean running;

    /**
     * 构造 jar 进程日志采集源。
     *
     * @param jarPath             jar 包路径（与进程启动参数匹配）
     * @param locator             进程日志定位器
     * @param stateDir            断点状态目录
     * @param pollInterval        文件 tail 轮询周期
     * @param locateRetryInterval 定位失败重试周期
     * @param listener            事件监听器
     */
    public JarProcessLogSource(String jarPath, ProcessLocator locator, Path stateDir,
                               Duration pollInterval, Duration locateRetryInterval,
                               LogEventListener listener) {
        this.jarPath = jarPath;
        this.locator = locator;
        this.stateDir = stateDir;
        this.pollInterval = pollInterval;
        this.locateRetryInterval = locateRetryInterval;
        this.listener = listener;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "logwatch-jar-" + Path.of(jarPath).getFileName());
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public String name() {
        return jarPath;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        if (!tryLocate()) {
            locateFuture = executor.scheduleWithFixedDelay(
                    this::retryLocateSafely,
                    locateRetryInterval.toMillis(),
                    locateRetryInterval.toMillis(),
                    TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        if (locateFuture != null) {
            locateFuture.cancel(false);
        }
        if (delegate != null) {
            delegate.stop();
        }
        executor.shutdownNow();
        log.info("jar 日志采集停止 jar={}", jarPath);
    }

    /**
     * 重试定位的调度入口：吞掉异常保证调度不中断。
     */
    private void retryLocateSafely() {
        try {
            if (tryLocate() && locateFuture != null) {
                locateFuture.cancel(false);
            }
        } catch (RuntimeException e) {
            log.warn("jar 进程定位重试失败 jar={}: {}", jarPath, e.toString());
        }
    }

    /**
     * 尝试定位进程日志并启动委托采集器。
     *
     * @return true 表示定位成功且采集已启动
     */
    private boolean tryLocate() {
        Optional<Path> logFile;
        try {
            logFile = locator.locateLogFile(jarPath);
        } catch (RuntimeException e) {
            log.warn("jar 进程定位失败 jar={}: {}", jarPath, e.toString());
            return false;
        }
        if (logFile.isEmpty()) {
            log.debug("jar 进程未找到或 stdout 非文件，稍后重试 jar={}", jarPath);
            return false;
        }
        // 事件 source 改写为 jar 路径，屏蔽底层日志文件路径
        delegate = new FileTailLogSource(logFile.get(), stateDir, pollInterval,
                e -> listener.onEvent(new LogEvent(jarPath, e.content(), e.timestamp())));
        delegate.start();
        log.info("jar 日志采集启动 jar={}, logFile={}", jarPath, logFile.get());
        return true;
    }
}
