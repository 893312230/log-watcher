package com.smartops.agent.intent;

import com.smartops.common.enums.IntentType;
import com.smartops.common.model.IntentResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * L1 正则规则意图识别器。
 *
 * <p>对应 agent.md 阶段二任务1。通过预定义的运维场景正则规则匹配用户输入，
 * 速度快、准确率高，但覆盖面有限。作为四层识别体系的第一道关卡。</p>
 *
 * <p>规则设计原则：
 * <ul>
 *   <li>查询指标：包含"查询/查看/当前"+ 指标关键词（CPU/内存/磁盘/QPS）</li>
 *   <li>趋势分析：包含"趋势/变化/对比"+ 时间范围词</li>
 *   <li>分析告警：包含"告警/报警/异常"+ 分析动词</li>
 *   <li>根因分析：包含"为什么/根因/原因"+ 服务/性能词</li>
 *   <li>执行操作：包含"重启/扩缩容/修改/部署"等动作动词</li>
 *   <li>知识问答：包含"如何/怎么/最佳实践/文档"等疑问词</li>
 * </ul></p>
 *
 * <p>线程安全：Pattern 不可变，组件单例，线程安全。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Component
public class L1RegexRecognizer implements IntentRecognizer {

    /** L1 具体规则的置信度：精确场景规则命中时高置信度，可直接短路（阈值 0.85）。 */
    private static final double CONCRETE_CONFIDENCE = 0.9;

    /**
     * L1 宽泛兜底规则的置信度：仅覆盖"查询/查看"等泛化动词，
     * 不足以单独定论，降级为 0.4 且不参与短路（低于 0.6 通用阈值），
     * 必须经 L2/L4 或冲突解决器确认。
     */
    private static final double BROAD_FALLBACK_CONFIDENCE = 0.4;

    /** 未命中任何规则时的置信度。 */
    private static final double NO_MATCH_CONFIDENCE = 0.2;

    /** 指标关键词正则：匹配 CPU、内存、磁盘、QPS 等常见运维指标。 */
    private static final Pattern METRIC_PATTERN = Pattern.compile(
            "(?i)(cpu|内存|memory|磁盘|disk|qps|请求量|响应时间|延迟|吞吐量|连接数|线程数)"
    );

    /** 各意图类型的规则列表：按优先级排序，先匹配先返回。 */
    private final List<Rule> rules;

    /**
     * 构造 L1 识别器，初始化预定义规则。
     */
    public L1RegexRecognizer() {
        this.rules = List.of(
                // 执行运维操作：含高风险动作动词（具体规则，可短路）
                new Rule(IntentType.EXECUTE_OPERATION,
                        Pattern.compile("(?i).*(重启|扩容|缩容|修改配置|部署|回滚|清理|停止|启动|kill).*"),
                        CONCRETE_CONFIDENCE),
                // 根因分析：含"为什么/根因"（具体规则，可短路）
                new Rule(IntentType.ROOT_CAUSE,
                        Pattern.compile("(?i).*(为什么|根因|根本原因|什么原因|导致|引起).*"),
                        CONCRETE_CONFIDENCE),
                // 趋势分析：含"趋势/对比/变化"+ 时间词（具体规则，可短路）
                new Rule(IntentType.TREND_ANALYSIS,
                        Pattern.compile("(?i).*(趋势|对比|变化|波动|历史|最近\\s*\\d+\\s*(分钟|小时|天)).*"),
                        CONCRETE_CONFIDENCE),
                // 分析告警：含"告警/报警"+ 分析动词（两种语序均支持，具体规则，可短路）
                new Rule(IntentType.ANALYZE_ALERT,
                        Pattern.compile("(?i).*(告警|报警|alert|异常|故障).*(分析|排查|查看|原因|严重).*"
                                + "|(?i).*(分析|排查|查看).*(告警|报警|alert).*"),
                        CONCRETE_CONFIDENCE),
                // 知识问答：含疑问词（具体规则，可短路）
                new Rule(IntentType.KNOWLEDGE_QA,
                        Pattern.compile("(?i).*(如何|怎么|怎样|最佳实践|文档|教程|配置方法|使用方法).*"),
                        CONCRETE_CONFIDENCE),
                // 查询指标：宽泛兜底正则，几乎吞噬一切查询类输入，
                // 降级为低置信度，不参与短路，交由后续层确认
                new Rule(IntentType.QUERY_METRIC,
                        Pattern.compile("(?i).*(查询|查看|当前|多少|状态|使用率|占用).*"),
                        BROAD_FALLBACK_CONFIDENCE)
        );
    }

    @Override
    public IntentResult recognize(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            throw new IllegalArgumentException("用户输入不能为 null 或空白");
        }

        for (Rule rule : rules) {
            Matcher matcher = rule.pattern().matcher(userInput);
            if (matcher.matches()) {
                Map<String, String> entities = extractEntities(userInput);
                return new IntentResult(rule.intentType(), rule.confidence(), getLayer(), entities);
            }
        }

        // 未命中任何规则，返回低置信度的 UNKNOWN
        return new IntentResult(IntentType.UNKNOWN, NO_MATCH_CONFIDENCE, getLayer(), null);
    }

    @Override
    public String getLayer() {
        return IntentResult.SOURCE_L1_REGEX;
    }

    /**
     * 从用户输入中提取实体信息（如指标名称）。
     *
     * @param userInput 用户输入
     * @return 实体 Map，可能为空
     */
    private Map<String, String> extractEntities(String userInput) {
        Matcher metricMatcher = METRIC_PATTERN.matcher(userInput);
        if (metricMatcher.find()) {
            return Map.of("metricName", normalizeMetric(metricMatcher.group(1)));
        }
        return Map.of();
    }

    /**
     * 将中文指标名统一为英文标识符。
     *
     * @param raw 原始指标名（可能是中文或英文）
     * @return 标准化后的指标名
     */
    private String normalizeMetric(String raw) {
        return switch (raw.toLowerCase()) {
            case "cpu" -> "cpu_usage";
            case "内存", "memory" -> "memory_usage";
            case "磁盘", "disk" -> "disk_usage";
            case "qps", "请求量" -> "qps";
            case "响应时间", "延迟" -> "latency";
            case "吞吐量" -> "throughput";
            case "连接数" -> "connections";
            case "线程数" -> "threads";
            default -> raw.toLowerCase();
        };
    }

    /**
     * 规则定义：意图类型 + 对应的正则模式 + 命中置信度。
     *
     * <p>具体场景规则置信度 0.9（可短路）；宽泛兜底规则置信度 0.4（不参与短路）。</p>
     *
     * @param intentType 意图类型
     * @param pattern    正则模式
     * @param confidence 命中时的置信度
     */
    private record Rule(IntentType intentType, Pattern pattern, double confidence) {
    }
}
