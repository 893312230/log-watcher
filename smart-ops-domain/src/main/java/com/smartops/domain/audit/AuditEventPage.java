package com.smartops.domain.audit;

import java.util.List;
import java.util.Objects;

/**
 * 审计事件分页结果。
 *
 * <p>线程安全：record 不可变，items 在构造时防御性拷贝。</p>
 *
 * @author smartops
 * @since 1.0.0
 *
 * @param items 当前页事件列表
 * @param total 符合条件的总条数
 * @param page  页码（从 0 开始）
 * @param size  每页大小
 */
public record AuditEventPage(
        List<AuditEvent> items,
        long total,
        int page,
        int size
) {

    /**
     * 紧凑构造器：结果集非空校验并防御性拷贝。
     */
    public AuditEventPage {
        Objects.requireNonNull(items, "结果集不能为 null");
        items = List.copyOf(items);
    }
}
