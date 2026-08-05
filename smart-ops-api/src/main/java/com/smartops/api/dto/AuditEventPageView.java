package com.smartops.api.dto;

import com.smartops.domain.audit.AuditEventPage;

import java.util.List;

/**
 * 审计事件分页结果视图。
 *
 * <p>REST 分页查询的统一输出格式，items 元素为 {@link AuditEventView}。</p>
 *
 * <p>线程安全：record 不可变。</p>
 *
 * @author smartops
 * @since 1.0.0
 *
 * @param items 当前页审计事件列表
 * @param total 符合条件的总条数
 * @param page  页码（从 0 开始）
 * @param size  每页大小
 */
public record AuditEventPageView(
        List<AuditEventView> items,
        long total,
        int page,
        int size
) {

    /**
     * 从领域分页结果构造视图。
     *
     * @param eventPage 领域分页结果
     * @return 分页视图
     */
    public static AuditEventPageView from(AuditEventPage eventPage) {
        return new AuditEventPageView(
                eventPage.items().stream().map(AuditEventView::from).toList(),
                eventPage.total(),
                eventPage.page(),
                eventPage.size());
    }
}
