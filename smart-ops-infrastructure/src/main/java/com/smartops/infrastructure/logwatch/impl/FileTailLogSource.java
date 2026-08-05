package com.smartops.infrastructure.logwatch.impl;

import com.smartops.common.exception.LogWatchException;
import com.smartops.domain.logwatch.LogEvent;
import com.smartops.infrastructure.logwatch.LogEventListener;
import com.smartops.infrastructure.logwatch.LogSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 文件 tail 日志采集源。
 *
 * <p>以固定周期轮询目标文件（{@code RandomAccessFile} + offset），
 * 比 WatchService 更省资源且不受事件丢失影响。特性：</p>
 * <ul>
 *   <li>断点续采：offset 与文件标识持久化到 stateDir，重启后从中断处继续</li>
 *   <li>轮替处理：文件截断（长度回退）或标识变更判定为轮替，从头重读</li>
 *   <li>多行合并：不匹配条目起始模式（默认日期开头）的行并入上一条，凑齐堆栈</li>
 *   <li>半行缓冲：无换行符的不完整行留待下次补齐后产出</li>
 *   <li>空闲冲刷：缓冲条目在无新数据时产出，避免依赖下一条触发</li>
 *   <li>初见跳历史：首次发现的文件从末尾开始采，避免历史日志告警风暴</li>
 * </ul>
 *
 * <p>线程约束：单采集线程驱动，{@link #start()}/{@link #stop()} 幂等。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public class FileTailLogSource implements LogSource {

    private static final Logger log = LoggerFactory.getLogger(FileTailLogSource.class);

    /** 默认条目起始模式：ISO 日期开头的行视为新日志条目。 */
    private static final Pattern DEFAULT_ENTRY_START = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}.*");

    /** 单次读取缓冲大小。 */
    private static final int READ_BUFFER_SIZE = 8192;

    /** 状态文件中的 offset 键。 */
    private static final String STATE_KEY_OFFSET = "offset";

    /** 状态文件中的文件标识键。 */
    private static final String STATE_KEY_FILE_KEY = "fileKey";

    private final Path file;
    private final Path stateDir;
    private final Duration pollInterval;
    private final LogEventListener listener;
    private final Pattern entryStartPattern;
    private final ScheduledExecutorService executor;

    /** 下次读取位置（字节偏移）。 */
    private long offset;

    /** 当前文件标识（轮替检测用），可能为 null（文件系统不支持）。 */
    private String fileKey;

    /** 半行缓冲：原始字节，避免 UTF-8 多字节字符被切断。 */
    private final ByteArrayOutputStream pendingBytes = new ByteArrayOutputStream();

    /** 当前累积中的多行条目。 */
    private final StringBuilder currentEntry = new StringBuilder();

    private volatile boolean running;
    private ScheduledFuture<?> future;

    /**
     * 构造文件 tail 采集源（默认条目起始模式：日期开头）。
     *
     * @param file         目标日志文件
     * @param stateDir     断点状态目录（自动创建）
     * @param pollInterval 轮询周期
     * @param listener     事件监听器
     */
    public FileTailLogSource(Path file, Path stateDir, Duration pollInterval, LogEventListener listener) {
        this.file = file;
        this.stateDir = stateDir;
        this.pollInterval = pollInterval;
        this.listener = listener;
        this.entryStartPattern = DEFAULT_ENTRY_START;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "logwatch-tail-" + file.getFileName());
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public String name() {
        return file.toString();
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        // 同步初始化 offset（含断点恢复/初见跳历史），避免启动竞态漏采或重采
        initPosition();
        future = executor.scheduleWithFixedDelay(
                this::pollSafely, 0, pollInterval.toMillis(), TimeUnit.MILLISECONDS);
        log.info("日志采集启动 file={}, offset={}", file, offset);
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        if (future != null) {
            future.cancel(false);
        }
        flushCurrentEntry();
        saveState();
        executor.shutdownNow();
        log.info("日志采集停止 file={}", file);
    }

    /**
     * 初始化读取位置：有状态文件则续采，否则跳到文件末尾（初见跳历史）。
     */
    private void initPosition() {
        try {
            Files.createDirectories(stateDir);
            loadState();
            if (!Files.exists(file)) {
                return;
            }
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
            String currentKey = fileKeyOf(attrs);
            if (fileKey != null && fileKey.equals(currentKey) && offset <= attrs.size()) {
                return; // 断点有效，保持已加载 offset
            }
            // 初见或文件已变更：从末尾开始
            fileKey = currentKey;
            offset = attrs.size();
            saveState();
        } catch (IOException e) {
            throw new LogWatchException("初始化采集位置失败: " + file, e);
        }
    }

    /**
     * 轮询入口：吞掉异常保证调度不中断。
     */
    private void pollSafely() {
        try {
            poll();
        } catch (Exception e) {
            log.warn("日志轮询失败 file={}: {}", file, e.toString());
        }
    }

    /**
     * 单轮轮询：检测轮替 → 读增量 → 处理完整行；无增量时空闲冲刷。
     */
    private void poll() throws IOException {
        if (!Files.exists(file)) {
            return;
        }
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
        String currentKey = fileKeyOf(attrs);
        long size = attrs.size();

        if ((fileKey != null && !fileKey.equals(currentKey)) || size < offset) {
            log.info("检测到日志轮替，从头重读 file={}", file);
            fileKey = currentKey;
            offset = 0;
            pendingBytes.reset();
            currentEntry.setLength(0);
        } else {
            fileKey = currentKey;
        }

        if (size > offset) {
            readIncrement();
            processCompleteLines();
            saveState();
        } else {
            flushCurrentEntry();
        }
    }

    /**
     * 读取从 offset 开始的增量字节并入半行缓冲。
     */
    private void readIncrement() throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            raf.seek(offset);
            byte[] buf = new byte[READ_BUFFER_SIZE];
            int n;
            while ((n = raf.read(buf)) > 0) {
                pendingBytes.write(buf, 0, n);
            }
            offset = raf.getFilePointer();
        }
    }

    /**
     * 从半行缓冲中切出完整行（以 \n 分隔，UTF-8 下 \n 字节不会出现在多字节字符内）。
     */
    private void processCompleteLines() {
        byte[] data = pendingBytes.toByteArray();
        int start = 0;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == '\n') {
                handleLine(decodeLine(data, start, i));
                start = i + 1;
            }
        }
        // 保留未完成的尾部半行
        pendingBytes.reset();
        pendingBytes.write(data, start, data.length - start);
    }

    /**
     * 解码单行并剥离行尾 \r。
     */
    private String decodeLine(byte[] data, int start, int end) {
        int len = end - start;
        if (len > 0 && data[end - 1] == '\r') {
            len--;
        }
        return new String(data, start, len, StandardCharsets.UTF_8);
    }

    /**
     * 处理一条完整行：条目起始行触发上一条冲刷，其余行并入当前条目。
     */
    private void handleLine(String line) {
        if (entryStartPattern.matcher(line).matches()) {
            flushCurrentEntry();
            currentEntry.append(line);
        } else if (currentEntry.length() > 0) {
            currentEntry.append('\n').append(line);
        } else {
            // 文件中段开始采集时，首行可能不是条目起始行，直接作为条目开始
            currentEntry.append(line);
        }
    }

    /**
     * 冲刷当前累积条目为日志事件。
     */
    private void flushCurrentEntry() {
        if (currentEntry.length() == 0) {
            return;
        }
        String content = currentEntry.toString();
        currentEntry.setLength(0);
        try {
            listener.onEvent(new LogEvent(name(), content, Instant.now()));
        } catch (RuntimeException e) {
            log.warn("日志事件消费失败 file={}: {}", file, e.toString());
        }
    }

    /**
     * 提取文件标识（部分文件系统不支持时返回 null）。
     */
    private String fileKeyOf(BasicFileAttributes attrs) {
        Object key = attrs.fileKey();
        return key == null ? null : key.toString();
    }

    /**
     * 状态文件路径（按文件路径哈希命名，避免路径字符问题）。
     */
    private Path stateFile() {
        return stateDir.resolve(Integer.toHexString(file.toString().hashCode()) + ".state");
    }

    /**
     * 加载断点状态。
     */
    private void loadState() {
        Path stateFile = stateFile();
        if (!Files.exists(stateFile)) {
            return;
        }
        Properties props = new Properties();
        try (var in = Files.newInputStream(stateFile)) {
            props.load(in);
            offset = Long.parseLong(props.getProperty(STATE_KEY_OFFSET, "0"));
            fileKey = props.getProperty(STATE_KEY_FILE_KEY);
        } catch (IOException | NumberFormatException e) {
            log.warn("断点状态损坏，忽略 file={}: {}", stateFile, e.toString());
        }
    }

    /**
     * 持久化断点状态（先写临时文件再原子移动）。
     */
    private void saveState() {
        Properties props = new Properties();
        props.setProperty(STATE_KEY_OFFSET, String.valueOf(offset));
        if (fileKey != null) {
            props.setProperty(STATE_KEY_FILE_KEY, fileKey);
        }
        Path stateFile = stateFile();
        Path tmp = stateFile.resolveSibling(stateFile.getFileName() + ".tmp");
        try (var out = Files.newOutputStream(tmp)) {
            props.store(out, "logwatch tail state");
            Files.move(tmp, stateFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("保存断点状态失败: " + stateFile, e);
        }
    }
}
