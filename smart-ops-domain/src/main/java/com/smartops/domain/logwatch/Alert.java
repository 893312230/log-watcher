package com.smartops.domain.logwatch;

import com.smartops.common.enums.AlertLevel;
import com.smartops.common.enums.AlertStatus;

import java.time.Instant;
import java.util.Objects;

/**
 * 告警模型。
 *
 * <p>分析管线的最终产出，经 {@code AlertRepository} 端口持久化，
 * 并经 {@code AlertNotifier} 端口实时推送。</p>
 *
 * <p>线程安全：record 不可变，状态流转通过 with* 方法返回副本。</p>
 *
 * @author smartops
 * @since 1.0.0
 *
 * @param id           持久化 id，未落库为 null
 * @param fingerprint  事件指纹（{@link LogEvent#fingerprint()}）
 * @param source       采集源标识
 * @param level        告警级别
 * @param keyword      命中的自定义关键字
 * @param message      日志摘要（首行）
 * @param stackTrace   堆栈/完整正文
 * @param analysis     分析结论（LLM 降级时含降级标注）
 * @param suggestion   解决建议
 * @param layerReached 分析到达的最高层级（0-4）
 * @param occurrence   时间窗内同类事件发生次数
 * @param status       处理状态
 * @param createdAt    创建时间
 * @param updatedAt    更新时间
 */
public record Alert(
        Long id,
        String fingerprint,
        String source,
        AlertLevel level,
        String keyword,
        String message,
        String stackTrace,
        String analysis,
        String suggestion,
        int layerReached,
        int occurrence,
        AlertStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * 紧凑构造器：必填字段非空校验。
     */
    public Alert {
        Objects.requireNonNull(fingerprint, "事件指纹不能为 null");
        Objects.requireNonNull(source, "采集源不能为 null");
        Objects.requireNonNull(level, "告警级别不能为 null");
        Objects.requireNonNull(message, "日志摘要不能为 null");
        Objects.requireNonNull(status, "处理状态不能为 null");
        Objects.requireNonNull(createdAt, "创建时间不能为 null");
        Objects.requireNonNull(updatedAt, "更新时间不能为 null");
    }

    /**
     * 创建新告警：默认 OPEN 状态、发生次数 1、分析与建议为空串。
     *
     * @param fingerprint  事件指纹
     * @param source       采集源标识
     * @param level        告警级别
     * @param keyword      命中的关键字，无则传空串
     * @param message      日志摘要
     * @param stackTrace   堆栈/完整正文
     * @param layerReached 分析到达的最高层级
     * @param now          当前时间
     * @return 新告警（无 id）
     */
    public static Alert create(String fingerprint, String source, AlertLevel level, String keyword,
                               String message, String stackTrace, int layerReached, Instant now) {
        Objects.requireNonNull(now, "当前时间不能为 null");
        return new Alert(null, fingerprint, source, level,
                keyword == null ? "" : keyword,
                message,
                stackTrace == null ? "" : stackTrace,
                "", "", layerReached, 1, AlertStatus.OPEN, now, now);
    }

    /**
     * 返回带持久化 id 的副本。
     *
     * @param id 持久化 id
     * @return 新实例
     */
    public Alert withId(long id) {
        return copy(id, analysis, suggestion, occurrence, status, updatedAt);
    }

    /**
     * 返回写入分析结论与建议的副本（同时刷新更新时间）。
     *
     * @param analysis   分析结论
     * @param suggestion 解决建议
     * @param now        当前时间
     * @return 新实例
     */
    public Alert withAnalysis(String analysis, String suggestion, Instant now) {
        return copy(id,
                analysis == null ? "" : analysis,
                suggestion == null ? "" : suggestion,
                occurrence, status, now);
    }

    /**
     * 返回合并发生次数后的副本（同时刷新更新时间）。
     *
     * @param occurrence 合并后的发生次数
     * @param now        当前时间
     * @return 新实例
     */
    public Alert withOccurrence(int occurrence, Instant now) {
        return copy(id, analysis, suggestion, occurrence, status, now);
    }

    /**
     * 返回状态流转后的副本（同时刷新更新时间）。
     *
     * @param status 新状态
     * @param now    当前时间
     * @return 新实例
     */
    public Alert withStatus(AlertStatus status, Instant now) {
        return copy(id, analysis, suggestion, occurrence, status, now);
    }

    /**
     * 复制实例的内部工厂。
     */
    private Alert copy(Long newId, String newAnalysis, String newSuggestion,
                       int newOccurrence, AlertStatus newStatus, Instant newUpdatedAt) {
        return new Alert(newId, fingerprint, source, level, keyword, message, stackTrace,
                newAnalysis, newSuggestion, layerReached, newOccurrence, newStatus,
                createdAt, newUpdatedAt);
    }
}
