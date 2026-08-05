package com.smartops.api.dto;

import com.smartops.domain.logwatch.AlertPage;

import java.util.List;

/**
 * 告警分页结果视图。
 *
 * <p>REST 分页查询的统一输出格式，items 元素为 {@link AlertView}。</p>
 *
 * <p>线程安全：record 不可变。</p>
 *
 * @author smartops
 * @since 1.0.0
 *
 * @param items 当前页告警列表
 * @param total 符合条件的总条数
 * @param page  页码（从 0 开始）
 * @param size  每页大小
 */
public record AlertPageView(
        List<AlertView> items,
        long total,
        int page,
        int size
) {

    /**
     * 从领域分页结果构造视图。
     *
     * @param alertPage 领域分页结果
     * @return 分页视图
     */
    public static AlertPageView from(AlertPage alertPage) {
        return new AlertPageView(
                alertPage.items().stream().map(AlertView::from).toList(),
                alertPage.total(),
                alertPage.page(),
                alertPage.size());
    }
}
