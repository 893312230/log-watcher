package com.smartops.infrastructure.logwatch.ml;

import com.smartops.common.enums.AlertLevel;
import com.smartops.domain.logwatch.ClassificationResult;
import com.smartops.domain.logwatch.LogEvent;
import com.smartops.domain.logwatch.port.LogLevelClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.tribuo.Dataset;
import org.tribuo.Example;
import org.tribuo.Model;
import org.tribuo.MutableDataset;
import org.tribuo.Prediction;
import org.tribuo.classification.Label;
import org.tribuo.classification.LabelFactory;
import org.tribuo.classification.sgd.linear.LinearSGDTrainer;
import org.tribuo.classification.sgd.objectives.LogMulticlass;
import org.tribuo.impl.ArrayExample;
import org.tribuo.math.optimisers.AdaGrad;
import org.tribuo.provenance.SimpleDataSourceProvenance;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * 基于 Tribuo 逻辑回归的日志级别分类器（阶段十五，ADR-019）。
 *
 * <p>构造时同步完成：加载 TSV 训练数据（{@code LEVEL<TAB>文本}，# 为注释）→
 * 词袋特征化 → 按类分层 8:2 切分 → SGD 逻辑回归训练 → 留出集准确率评估。
 * 准确率低于 {@code minAccuracy} 或数据异常时 {@link #isReady()} 为 false，
 * 管线层据此直通旧行为（评测基线门禁，不达标不上线）。</p>
 *
 * <p>降级契约：{@link #classify(String)} 永不抛异常，
 * 任何失败返回 {@link ClassificationResult#abstain()}。</p>
 *
 * <p>线程安全：训练仅发生在构造期，之后模型只读，推理可多线程并发。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public class TribuoLogLevelClassifier implements LogLevelClassifier {

    private static final Logger log = LoggerFactory.getLogger(TribuoLogLevelClassifier.class);

    /** 分层切分的训练集占比。 */
    private static final double TRAIN_RATIO = 0.8;

    /** 每个类别参与训练的最小样本数（少于该值视为数据不足）。 */
    private static final int MIN_SAMPLES_PER_CLASS = 3;

    /** SGD 训练轮数（小样本语料默认 5 轮欠拟合，实测 50 轮留出集收敛）。 */
    private static final int TRAIN_EPOCHS = 50;

    /** SGD 训练日志间隔（输出无用，设为大于轮数关闭）。 */
    private static final int TRAIN_LOG_INTERVAL = -1;

    /** SGD 小批次大小（1 即纯随机梯度）。 */
    private static final int TRAIN_MINIBATCH = 1;

    private final double minAccuracy;
    private volatile Model<Label> model;
    private volatile boolean ready;
    private volatile double holdoutAccuracy;

    /**
     * 构造即训练。
     *
     * @param trainingData 训练数据资源（TSV：LEVEL TAB 单行文本，# 注释）
     * @param minAccuracy  留出集准确率下限，低于则分类器不启用
     * @param seed         切分随机种子（固定可复现）
     */
    public TribuoLogLevelClassifier(Resource trainingData, double minAccuracy, long seed) {
        this.minAccuracy = minAccuracy;
        try {
            Map<AlertLevel, List<String>> samples = loadSamples(trainingData);
            train(samples, seed);
        } catch (RuntimeException | java.io.IOException e) {
            log.warn("ML 定级分类器初始化失败，保持未就绪（管线回退旧行为）: {}", e.toString());
        }
    }

    @Override
    public ClassificationResult classify(String content) {
        if (!ready || model == null) {
            return ClassificationResult.abstain();
        }
        try {
            List<String> tokens = tokenize(content);
            if (tokens.isEmpty()) {
                return ClassificationResult.abstain();
            }
            ArrayExample<Label> example = new ArrayExample<>(new Label("UNKNOWN"));
            for (String token : tokens) {
                example.add(featureName(token), 1.0);
            }
            Prediction<Label> prediction = model.predict(example);
            Label output = prediction.getOutput();
            AlertLevel level = AlertLevel.valueOf(output.getLabel());
            double confidence = Math.max(0.0, Math.min(1.0, output.getScore()));
            return new ClassificationResult(level, confidence);
        } catch (RuntimeException e) {
            log.warn("ML 定级推理失败，按弃权处理: {}", e.toString());
            return ClassificationResult.abstain();
        }
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    /**
     * 留出集准确率（训练评估报告与监控用）。
     *
     * @return 最近一次训练留出集准确率，未训练时为 0
     */
    public double getHoldoutAccuracy() {
        return holdoutAccuracy;
    }

    /** 加载 TSV 并按类别分组；无效行跳过计数。 */
    private Map<AlertLevel, List<String>> loadSamples(Resource resource) throws java.io.IOException {
        Map<AlertLevel, List<String>> samples = new EnumMap<>(AlertLevel.class);
        int skipped = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int tab = trimmed.indexOf('\t');
                if (tab <= 0 || tab == trimmed.length() - 1) {
                    skipped++;
                    continue;
                }
                try {
                    AlertLevel level = AlertLevel.valueOf(
                            trimmed.substring(0, tab).trim().toUpperCase(Locale.ROOT));
                    samples.computeIfAbsent(level, k -> new ArrayList<>())
                            .add(trimmed.substring(tab + 1).trim());
                } catch (IllegalArgumentException e) {
                    skipped++;
                }
            }
        }
        if (skipped > 0) {
            log.info("ML 定级训练数据跳过无效行 {} 条", skipped);
        }
        return samples;
    }

    /** 分层切分 + 训练 + 留出集评估；任一类别样本不足或准确率不达标则不启用。 */
    private void train(Map<AlertLevel, List<String>> samples, long seed) {
        LabelFactory factory = new LabelFactory();
        MutableDataset<Label> trainSet = new MutableDataset<>(
                new SimpleDataSourceProvenance("log-level-seed-train", factory), factory);
        MutableDataset<Label> testSet = new MutableDataset<>(
                new SimpleDataSourceProvenance("log-level-seed-test", factory), factory);

        Random random = new Random(seed);
        int total = 0;
        for (Map.Entry<AlertLevel, List<String>> entry : samples.entrySet()) {
            List<String> texts = new ArrayList<>(entry.getValue());
            if (texts.size() < MIN_SAMPLES_PER_CLASS) {
                log.warn("ML 定级类别 {} 样本不足（{} 条），分类器不启用", entry.getKey(), texts.size());
                return;
            }
            java.util.Collections.shuffle(texts, random);
            int trainCount = (int) Math.ceil(texts.size() * TRAIN_RATIO);
            for (int i = 0; i < texts.size(); i++) {
                MutableDataset<Label> target = i < trainCount ? trainSet : testSet;
                target.add(toExample(texts.get(i), entry.getKey()));
            }
            total += texts.size();
        }
        if (testSet.size() == 0) {
            log.warn("ML 定级留出集为空，无法评估，分类器不启用");
            return;
        }

        Model<Label> trained = new LinearSGDTrainer(new LogMulticlass(), new AdaGrad(0.1),
                TRAIN_EPOCHS, TRAIN_LOG_INTERVAL, TRAIN_MINIBATCH, seed).train(trainSet);
        double accuracy = evaluate(trained, testSet);
        this.holdoutAccuracy = accuracy;
        if (accuracy < minAccuracy) {
            log.warn("ML 定级留出集准确率 {} 低于门禁 {}，分类器不启用（总样本 {}）",
                    String.format("%.3f", accuracy), minAccuracy, total);
            return;
        }
        this.model = trained;
        this.ready = true;
        log.info("ML 定级分类器就绪：总样本 {}，训练 {}，留出 {}，留出集准确率 {}",
                total, trainSet.size(), testSet.size(), String.format("%.3f", accuracy));
    }

    /**
     * 留出集整体准确率。逐条预测而非 {@code LabelEvaluator} 批量评估：
     * 特征与训练集零重叠的样本会令 Tribuo 抛 {@link IllegalArgumentException}，
     * 此类样本按判错计入（生产推理同样会弃权，口径一致）。
     */
    private static double evaluate(Model<Label> model, Dataset<Label> testSet) {
        int correct = 0;
        int total = 0;
        for (Example<Label> example : testSet) {
            total++;
            try {
                Prediction<Label> prediction = model.predict(example);
                if (prediction.getOutput().getLabel().equals(example.getOutput().getLabel())) {
                    correct++;
                }
            } catch (IllegalArgumentException e) {
                // 与训练特征零重叠的样本无法预测，按判错处理
            }
        }
        return total == 0 ? 0.0 : (double) correct / total;
    }

    /** 文本转 Tribuo 样本：二元词袋特征。 */
    private Example<Label> toExample(String text, AlertLevel level) {
        ArrayExample<Label> example = new ArrayExample<>(new Label(level.name()));
        for (String token : tokenize(text)) {
            example.add(featureName(token), 1.0);
        }
        return example;
    }

    /**
     * 简单分词：先经 {@link LogEvent#normalizeDynamicParts(String)} 消除动态噪声
     * （IP/数字/时间戳，与指纹同一口径），小写后按非字母数字切分，去单字符噪声，
     * 输出去重一元词袋特征。bigram/字符 n-gram 在种子语料上调参实测反而降分
     * （特征稀疏），故仅保留一元。
     */
    private static List<String> tokenize(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        String[] raw = LogEvent.normalizeDynamicParts(content)
                .toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        java.util.Set<String> dedup = new java.util.LinkedHashSet<>();
        for (String token : raw) {
            if (token.length() >= 2) {
                dedup.add(token);
            }
        }
        return List.copyOf(dedup);
    }

    private static String featureName(String token) {
        return "tok=" + token;
    }
}
