package com.smartops.common.enums;

/**
 * 告警级别枚举。
 *
 * <p>描述日志监控（logwatch）检出的告警严重度，
 * ordinal 即严重度排序：ERROR 最严重，INFO 最轻。</p>
 *
 * <p>线程安全：枚举天然不可变。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public enum AlertLevel {

    /**
     * 错误：服务已发生故障或抛出异常，需要立即关注。
     */
    ERROR,

    /**
     * 警告：存在潜在风险但服务尚未失败。
     */
    WARN,

    /**
     * 提示：一般性信息，通常被分析管线直接丢弃，仅保留兜底。
     */
    INFO,
}
