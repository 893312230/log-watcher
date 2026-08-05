package com.smartops.common.enums;

/**
 * 告警处理状态枚举。
 *
 * <p>描述一条告警从产生到关闭的处置生命周期：
 * OPEN（待处理）→ ACKED（已确认）→ RESOLVED（已解决）。</p>
 *
 * <p>线程安全：枚举天然不可变。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public enum AlertStatus {

    /**
     * 待处理：告警已生成，尚未有人确认。
     */
    OPEN,

    /**
     * 已确认：运维人员已知悉该告警。
     */
    ACKED,

    /**
     * 已解决：告警对应问题已处理完毕。
     */
    RESOLVED,
}
