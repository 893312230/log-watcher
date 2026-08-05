package com.smartops.domain.logwatch.port;

import com.smartops.domain.logwatch.Alert;

/**
 * 告警实时通知端口。
 *
 * <p>实现位于 smart-ops-infrastructure（SSE Sinks），
 * 由分析管线在告警落库后调用，向在线订阅者推送。
 * 实现必须非阻塞：无订阅者或缓冲满时直接丢弃，不得拖慢分析线程。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public interface AlertNotifier {

    /**
     * 发布一条告警（尽力投递，不保证到达）。
     *
     * @param alert 已落库的告警
     */
    void publish(Alert alert);
}
