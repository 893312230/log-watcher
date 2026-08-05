package com.smartops.agent.logwatch.anomaly;

import com.smartops.domain.logwatch.LogEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于统计基线的异常检测器（阶段七）。
 *
 * <p>为每个日志来源维护指数移动平均（EMA）与 RMS 偏差，
 * 当前事件间隔偏离基线超过 2 倍 RMS 时评分为偏离比例。</p>
 *
 * <p>线程安全：ConcurrentHashMap 保护 per-source 状态。</p>
 */
public class StatisticalBaselineDetector implements AnomalyDetector {

    /** 基线滑动因子：alpha=0.2，约 5 个样本主导 EMA。 */
    private static final double ALPHA = 0.2;

    /** 正常间隔（毫秒）的默认初始值。 */
    private static final double DEFAULT_MEAN_INTERVAL_MS = 5000.0;

    /** 触发高分的偏离倍数。 */
    private static final double DEVIATION_MULTIPLIER = 2.0;

    private final double threshold;

    /** 每个来源（source path）的基线状态。 */
    private final Map<String, Baseline> baselines = new ConcurrentHashMap<>();

    public StatisticalBaselineDetector(double threshold) {
        this.threshold = threshold;
    }

    @Override
    public synchronized double score(LogEvent event) {
        String source = event.source();
        Baseline b = baselines.computeIfAbsent(source,
                k -> new Baseline(DEFAULT_MEAN_INTERVAL_MS));
        long now = System.currentTimeMillis();

        double interval = (double) (now - b.lastTimestamp);
        double oldMean = b.mean;
        double oldRms = b.rms;
        double deviation = Math.abs(interval - oldMean)
                / Math.max(oldRms * DEVIATION_MULTIPLIER, 1.0);
        double score = Math.min(deviation, 1.0);

        // update EMA using OLD mean for variance, then update mean
        b.rms = Math.sqrt(
                oldRms * oldRms * (1 - ALPHA)
                        + (interval - oldMean) * (interval - oldMean) * ALPHA);
        b.mean = oldMean * (1 - ALPHA) + interval * ALPHA;
        b.lastTimestamp = now;

        return score >= threshold ? score : 0.0;
    }

    @Override
    public double threshold() {
        return threshold;
    }

    /** 当前已知的来源基线快照（供查询）。 */
    public Map<String, BaselineSnapshot> snapshots() {
        Map<String, BaselineSnapshot> result = new ConcurrentHashMap<>();
        baselines.forEach((k, v) ->
                result.put(k, new BaselineSnapshot(v.mean, v.rms, v.lastTimestamp)));
        return result;
    }

    /** per-source 基线状态。 */
    private static class Baseline {
        double mean;
        double rms;
        long lastTimestamp;

        Baseline(double mean) {
            this.mean = mean;
            this.rms = mean * 0.5;
            this.lastTimestamp = System.currentTimeMillis();
        }
    }

    /** 只读快照。 */
    public record BaselineSnapshot(double meanMs, double rmsMs, long lastTimestampMs) {}
}
