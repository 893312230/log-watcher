package com.smartops.agent.logwatch.pipeline.impl;

import com.smartops.agent.logwatch.pipeline.AnalysisLayer;
import com.smartops.domain.logwatch.AnalysisContext;
import com.smartops.domain.logwatch.AnalysisOutcome;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L0 规则抑制层。
 *
 * <p>按事件指纹做时间窗去重：窗内同指纹事件直接 SUPPRESS 并累计计数；
 * 窗口静默后再次出现的事件放行，并把窗口内被抑制次数合并进
 * {@link AnalysisContext#getOccurrence()}，防止高频重复错误引发告警风暴。</p>
 *
 * <p>线程安全：窗口表使用 {@link ConcurrentHashMap}。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public class L0SuppressionLayer implements AnalysisLayer {

    /** 窗口表默认容量上限，超出淘汰最旧窗口。 */
    private static final int DEFAULT_MAX_ENTRIES = 10_000;

    private final Duration window;
    private final Clock clock;
    private final int maxEntries;
    private final Map<String, WindowEntry> windows = new ConcurrentHashMap<>();

    /**
     * 构造 L0 抑制层（默认容量上限）。
     *
     * @param window 抑制时间窗
     * @param clock  时钟（测试可注入假时钟）
     */
    public L0SuppressionLayer(Duration window, Clock clock) {
        this(window, clock, DEFAULT_MAX_ENTRIES);
    }

    /**
     * 构造 L0 抑制层。
     *
     * @param window     抑制时间窗
     * @param clock      时钟
     * @param maxEntries 窗口表容量上限
     */
    public L0SuppressionLayer(Duration window, Clock clock, int maxEntries) {
        this.window = window;
        this.clock = clock;
        this.maxEntries = maxEntries;
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public AnalysisOutcome apply(AnalysisContext context) {
        context.markLayerReached(0);
        Instant now = clock.instant();
        WindowEntry entry = windows.get(context.getFingerprint());

        if (entry != null && Duration.between(entry.lastSeen, now).compareTo(window) < 0) {
            entry.count++;
            entry.lastSeen = now;
            return AnalysisOutcome.suppress();
        }

        int merged = entry == null ? 1 : entry.count + 1;
        windows.remove(context.getFingerprint());
        context.incrementOccurrence(merged - 1);
        windows.put(context.getFingerprint(), new WindowEntry(now));
        evictIfNeeded();
        return AnalysisOutcome.proceed();
    }

    /**
     * 容量超限时淘汰最旧窗口（简单线性扫描，容量小可接受）。
     */
    private void evictIfNeeded() {
        if (windows.size() <= maxEntries) {
            return;
        }
        String oldestKey = null;
        Instant oldest = Instant.MAX;
        for (Map.Entry<String, WindowEntry> e : windows.entrySet()) {
            if (e.getValue().lastSeen.isBefore(oldest)) {
                oldest = e.getValue().lastSeen;
                oldestKey = e.getKey();
            }
        }
        if (oldestKey != null) {
            windows.remove(oldestKey);
        }
    }

    /**
     * 指纹窗口条目（可变，仅在窗口表内使用）。
     */
    private static final class WindowEntry {
        private int count = 1;
        private Instant lastSeen;

        private WindowEntry(Instant lastSeen) {
            this.lastSeen = lastSeen;
        }
    }
}
