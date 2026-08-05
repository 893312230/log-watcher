package com.smartops.domain.logwatch;

import com.smartops.common.enums.AlertLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 分析上下文。
 *
 * <p>在分析管线各层间传递并逐层累积的可变上下文：
 * 事件与指纹构造即定（只读），级别、命中关键字、知识引用、
 * 分析结论、解决建议、到达层级、升级标记由各层写入。</p>
 *
 * <p>线程约束：非线程安全，按设计仅在分析线程内单线程流转。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public final class AnalysisContext {

    /** 原始日志事件（只读）。 */
    private final LogEvent event;

    /** 事件指纹（只读，构造时计算一次）。 */
    private final String fingerprint;

    /** 告警级别（L1 正则或 L2 ML 定级层写入）。 */
    private AlertLevel level;

    /** 命中的自定义关键字（预过滤写入）。 */
    private String matchedKeyword;

    /** 发生次数（L0 合并同类告警时累加，初始 1）。 */
    private int occurrence = 1;

    /** L2 知识库命中的参考条目（来源路径列表）。 */
    private final List<String> knowledgeRefs = new ArrayList<>();

    /** 分析结论（L3/L4 写入）。 */
    private String analysis;

    /** 解决建议（L3/L4 写入）。 */
    private String suggestion;

    /** 到达过的最高层级（0=L0 … 5=L5），只增不减。 */
    private int layerReached;

    /** 是否需升级 Supervisor 会诊（L3 标记，L4 消费）。 */
    private boolean escalate;

    /**
     * 构造分析上下文。
     *
     * @param event 原始日志事件，不能为 null
     */
    public AnalysisContext(LogEvent event) {
        this.event = Objects.requireNonNull(event, "日志事件不能为 null");
        this.fingerprint = event.fingerprint();
    }

    /**
     * 记录到达层级，只保留历史最高值。
     *
     * @param layer 层级编号（0-5）
     */
    public void markLayerReached(int layer) {
        if (layer > this.layerReached) {
            this.layerReached = layer;
        }
    }

    /**
     * 追加一条知识库参考。
     *
     * @param ref 知识来源标识（如 runbooks/db-timeout.md）
     */
    public void addKnowledgeRef(String ref) {
        this.knowledgeRefs.add(ref);
    }

    /**
     * 累加发生次数（L0 时间窗内同类事件合并）。
     *
     * @param delta 增量，正数
     */
    public void incrementOccurrence(int delta) {
        this.occurrence += delta;
    }

    /**
     * 标记需要升级 Supervisor 会诊。
     */
    public void markEscalate() {
        this.escalate = true;
    }

    /**
     * 获取原始日志事件。
     *
     * @return 日志事件
     */
    public LogEvent getEvent() {
        return event;
    }

    /**
     * 获取事件指纹。
     *
     * @return 64 位十六进制指纹
     */
    public String getFingerprint() {
        return fingerprint;
    }

    /**
     * 获取告警级别。
     *
     * @return 级别，L1 之前为 null
     */
    public AlertLevel getLevel() {
        return level;
    }

    /**
     * 写入告警级别。
     *
     * @param level 告警级别
     */
    public void setLevel(AlertLevel level) {
        this.level = level;
    }

    /**
     * 获取命中的自定义关键字。
     *
     * @return 关键字，未命中为 null
     */
    public String getMatchedKeyword() {
        return matchedKeyword;
    }

    /**
     * 写入命中的自定义关键字。
     *
     * @param matchedKeyword 关键字
     */
    public void setMatchedKeyword(String matchedKeyword) {
        this.matchedKeyword = matchedKeyword;
    }

    /**
     * 获取发生次数。
     *
     * @return 发生次数，初始 1
     */
    public int getOccurrence() {
        return occurrence;
    }

    /**
     * 获取知识库参考列表（只读视图）。
     *
     * @return 参考列表
     */
    public List<String> getKnowledgeRefs() {
        return Collections.unmodifiableList(knowledgeRefs);
    }

    /**
     * 获取分析结论。
     *
     * @return 分析结论，未分析为 null
     */
    public String getAnalysis() {
        return analysis;
    }

    /**
     * 写入分析结论。
     *
     * @param analysis 分析结论
     */
    public void setAnalysis(String analysis) {
        this.analysis = analysis;
    }

    /**
     * 获取解决建议。
     *
     * @return 解决建议，未生成为 null
     */
    public String getSuggestion() {
        return suggestion;
    }

    /**
     * 写入解决建议。
     *
     * @param suggestion 解决建议
     */
    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    /**
     * 获取到达过的最高层级。
     *
     * @return 层级编号（0-5）
     */
    public int getLayerReached() {
        return layerReached;
    }

    /**
     * 是否需升级 Supervisor 会诊。
     *
     * @return true 表示需升级
     */
    public boolean isEscalate() {
        return escalate;
    }
}
