package com.smartops.api.support;

import java.util.List;

/**
 * 列表端点内存分页工具（阶段十三 WS5）。
 *
 * <p>为小表（Runbook/Webhook 订阅/静默/SLO/通知渠道/集成）列表端点
 * 统一提供 page/size 切片：size 默认 100、上限 500，防止无限增长的全量返回。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public final class PageSlice {

    /** 默认每页大小。 */
    public static final int DEFAULT_SIZE = 100;

    /** 每页大小上限。 */
    public static final int MAX_SIZE = 500;

    private PageSlice() {
    }

    /**
     * 对全量列表切片。
     *
     * @param all  全量列表
     * @param page 页码（0 起始，null/负数按 0）
     * @param size 每页大小（null 按默认，夹紧到 [1, MAX_SIZE]）
     * @param <T>  元素类型
     * @return 当前页子列表（越界时为空列表）
     */
    public static <T> List<T> slice(List<T> all, Integer page, Integer size) {
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? DEFAULT_SIZE : Math.min(Math.max(1, size), MAX_SIZE);
        int from = Math.min(p * s, all.size());
        return all.subList(from, Math.min(from + s, all.size()));
    }
}
