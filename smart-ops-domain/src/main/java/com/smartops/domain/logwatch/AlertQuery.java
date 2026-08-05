package com.smartops.domain.logwatch;

import com.smartops.common.enums.AlertLevel;

import java.time.Instant;

/**
 * 告警分页查询条件。
 *
 * <p>过滤字段均可为 null（表示不过滤）；分页参数在构造时归一化：
 * 页码下限 0，每页大小收敛到 1..{@value #MAX_PAGE_SIZE}。</p>
 *
 * <p>线程安全：record 不可变。</p>
 *
 * @author smartops
 * @since 1.0.0
 *
 * @param level   按级别过滤，null 不过滤
 * @param source  按采集源包含匹配过滤，null 不过滤
 * @param keyword 按告警关键字包含匹配过滤，null 不过滤
 * @param from    创建时间下限（含），null 不限
 * @param to      创建时间上限（含），null 不限
 * @param page    页码，从 0 开始
 * @param size    每页大小，1..100
 */
public record AlertQuery(
        AlertLevel level,
        String source,
        String keyword,
        Instant from,
        Instant to,
        int page,
        int size
) {

    /** 每页大小上限，防止全表拉取。 */
    public static final int MAX_PAGE_SIZE = 100;

    /**
     * 紧凑构造器：分页参数归一化。
     */
    public AlertQuery {
        page = Math.max(page, 0);
        size = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }
}
