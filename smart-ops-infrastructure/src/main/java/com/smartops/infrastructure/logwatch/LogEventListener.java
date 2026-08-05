package com.smartops.infrastructure.logwatch;

import com.smartops.domain.logwatch.LogEvent;

/**
 * 日志事件监听器。
 *
 * <p>采集源每产出一条逻辑日志（多行已合并）回调一次，
 * 由分析管线服务注册消费。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@FunctionalInterface
public interface LogEventListener {

    /**
     * 处理一条日志事件。
     *
     * <p>实现必须快速返回（如仅入队），阻塞会拖慢采集线程。</p>
     *
     * @param event 日志事件
     */
    void onEvent(LogEvent event);
}
