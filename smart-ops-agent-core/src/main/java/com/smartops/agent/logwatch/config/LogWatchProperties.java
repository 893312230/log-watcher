package com.smartops.agent.logwatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 日志采集分析配置属性（{@code smartops.logwatch.*}）。
 *
 * <p>以 JavaBean 绑定支持 {@code sources} 列表（@Value 无法绑定对象列表）；
 * 所有键在 application.yml 中带中文注释，缺省值即生产小内存安全值。</p>
 *
 * <p>线程安全：仅在容器启动期绑定，之后只读。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "smartops.logwatch")
public class LogWatchProperties {

    /** 总开关（@ConditionalOnProperty 消费，此处仅为绑定完整）。 */
    private boolean enabled;

    /** 采集源列表。 */
    private List<Source> sources = new ArrayList<>();

    /** 排除子串（命中即不告警，屏蔽采集管道自身日志的自引用告警）。 */
    private List<String> excludeKeywords = new ArrayList<>();

    /** 文件 tail 轮询间隔（毫秒）。 */
    private long pollIntervalMs = 500;

    /** 采集断点状态目录（offset 持久化，重启续采）。 */
    private String stateDir = "./logwatch-state";

    /** 分析队列容量（背压边界，满则丢弃并计数）。 */
    private int queueCapacity = 2000;

    /** L0 抑制层参数。 */
    private L0 l0 = new L0();

    /** ML 机器学习定级层参数（阶段十五）。 */
    private Ml ml = new Ml();

    /** L3 LLM 层参数。 */
    private L3 l3 = new L3();

    /** L4 会诊层参数。 */
    private L4 l4 = new L4();

    /** SSE 推送参数。 */
    private Sse sse = new Sse();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<Source> getSources() {
        return sources;
    }

    public void setSources(List<Source> sources) {
        this.sources = sources;
    }

    public List<String> getExcludeKeywords() {
        return excludeKeywords;
    }

    public void setExcludeKeywords(List<String> excludeKeywords) {
        this.excludeKeywords = excludeKeywords;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public String getStateDir() {
        return stateDir;
    }

    public void setStateDir(String stateDir) {
        this.stateDir = stateDir;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public L0 getL0() {
        return l0;
    }

    public void setL0(L0 l0) {
        this.l0 = l0;
    }

    public Ml getMl() {
        return ml;
    }

    public void setMl(Ml ml) {
        this.ml = ml;
    }

    public L3 getL3() {
        return l3;
    }

    public void setL3(L3 l3) {
        this.l3 = l3;
    }

    public L4 getL4() {
        return l4;
    }

    public void setL4(L4 l4) {
        this.l4 = l4;
    }

    public Sse getSse() {
        return sse;
    }

    public void setSse(Sse sse) {
        this.sse = sse;
    }

    /**
     * 单个采集源：file 为本机日志文件，jar 为运行中 jar 进程日志。
     */
    public static class Source {

        /** 源类型：file | jar。 */
        private String type;

        /** file 为日志文件路径；jar 为 jar 包路径。 */
        private String path;

        /** 该源自定义告警关键字（大小写不敏感子串）。 */
        private List<String> keywords = new ArrayList<>();

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public List<String> getKeywords() {
            return keywords;
        }

        public void setKeywords(List<String> keywords) {
            this.keywords = keywords;
        }
    }

    /**
     * L0 告警抑制层参数。
     */
    public static class L0 {

        /** 同指纹合并窗口（秒），防告警风暴。 */
        private long windowSeconds = 300;

        public long getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(long windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
    }

    /**
     * ML 机器学习定级层参数（阶段十五，ADR-019：L1 正则漏判救援）。
     */
    public static class Ml {

        /** 开关：开启后 L1 未命中改为放行待定，由 ML 层裁决。 */
        private boolean enabled = false;

        /** 采信 ML 预测的置信度下限（低于则按旧行为抑制）。 */
        private double confidenceThreshold = 0.85;

        /** 启动训练留出集准确率下限（低于则分类器不启用，管线回退旧行为）。 */
        private double minAccuracy = 0.8;

        /** 外部训练数据 TSV 路径（为空用 classpath 内置种子数据）。 */
        private String trainingDataPath;

        /** 训练切分随机种子（固定可复现）。 */
        private long seed = 42;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getConfidenceThreshold() {
            return confidenceThreshold;
        }

        public void setConfidenceThreshold(double confidenceThreshold) {
            this.confidenceThreshold = confidenceThreshold;
        }

        public double getMinAccuracy() {
            return minAccuracy;
        }

        public void setMinAccuracy(double minAccuracy) {
            this.minAccuracy = minAccuracy;
        }

        public String getTrainingDataPath() {
            return trainingDataPath;
        }

        public void setTrainingDataPath(String trainingDataPath) {
            this.trainingDataPath = trainingDataPath;
        }

        public long getSeed() {
            return seed;
        }

        public void setSeed(long seed) {
            this.seed = seed;
        }
    }

    /**
     * L3 LLM 根因分析层参数。
     */
    public static class L3 {

        /** 每分钟 LLM 调用上限（费用保护）。 */
        private int ratePerMinute = 10;

        /** 同指纹窗口内合并次数达到该值时升级 L4 会诊。 */
        private int escalateOccurrenceThreshold = 3;

        public int getRatePerMinute() {
            return ratePerMinute;
        }

        public void setRatePerMinute(int ratePerMinute) {
            this.ratePerMinute = ratePerMinute;
        }

        public int getEscalateOccurrenceThreshold() {
            return escalateOccurrenceThreshold;
        }

        public void setEscalateOccurrenceThreshold(int escalateOccurrenceThreshold) {
            this.escalateOccurrenceThreshold = escalateOccurrenceThreshold;
        }
    }

    /**
     * L4 Supervisor 会诊层参数。
     */
    public static class L4 {

        /** 每日会诊次数上限（费用保护）。 */
        private int dailyLimit = 20;

        public int getDailyLimit() {
            return dailyLimit;
        }

        public void setDailyLimit(int dailyLimit) {
            this.dailyLimit = dailyLimit;
        }
    }

    /**
     * SSE 实时推送参数。
     */
    public static class Sse {

        /** 背压缓冲容量（慢订阅者积压上界，超出丢弃）。 */
        private int bufferSize = 256;

        public int getBufferSize() {
            return bufferSize;
        }

        public void setBufferSize(int bufferSize) {
            this.bufferSize = bufferSize;
        }
    }
}
