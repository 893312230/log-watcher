package com.smartops.api.dto;

import com.smartops.common.enums.AlertLevel;
import com.smartops.common.enums.AlertStatus;
import com.smartops.domain.logwatch.Alert;

import java.time.Instant;

/**
 * 告警视图 DTO。
 *
 * <p>REST 查询与 SSE 推送的统一输出格式，屏蔽领域模型中
 * 不适合外发的字段（如完整指纹只保留前 12 位展示）。</p>
 *
 * <p>线程安全：record 不可变。</p>
 *
 * @author smartops
 * @since 1.0.0
 *
 * @param id           告警 id
 * @param fingerprint  事件指纹前缀（前 12 位，供定位同源告警）
 * @param source       日志来源
 * @param level        告警级别
 * @param keyword      命中的关键字
 * @param message      日志摘要
 * @param stackTrace   完整日志内容（含堆栈）
 * @param analysis     分析结论
 * @param suggestion   解决建议
 * @param layerReached 分析到达的最高层级（0-4）
 * @param occurrence   窗口内发生次数
 * @param status       处理状态
 * @param createdAt    创建时间
 */
public record AlertView(
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

    /** 指纹对外展示长度。 */
    private static final int FINGERPRINT_DISPLAY_LENGTH = 12;

    /**
     * 从领域告警构造视图。
     *
     * @param alert 领域告警
     * @return 告警视图
     */
    public static AlertView from(Alert alert) {
        String fp = alert.fingerprint();
        return new AlertView(
                alert.id(),
                fp.length() <= FINGERPRINT_DISPLAY_LENGTH
                        ? fp
                        : fp.substring(0, FINGERPRINT_DISPLAY_LENGTH),
                alert.source(),
                alert.level(),
                alert.keyword(),
                alert.message(),
                alert.stackTrace(),
                alert.analysis(),
                alert.suggestion(),
                alert.layerReached(),
                alert.occurrence(),
                alert.status(),
                alert.createdAt(),
                alert.updatedAt());
    }
}
