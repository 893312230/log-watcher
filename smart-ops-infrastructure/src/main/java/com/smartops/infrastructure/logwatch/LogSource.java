package com.smartops.infrastructure.logwatch;

/**
 * 日志采集源。
 *
 * <p>实现位于 impl 子包：{@code FileTailLogSource}（任意日志文件 tail）、
 * {@code JarProcessLogSource}（指定运行中 jar 包，定位进程输出日志后 tail）。
 * 生命周期由 {@code LogWatchConfig} 装配管理：应用启动时 {@link #start()}，
 * 关闭时 {@link #stop()}。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public interface LogSource {

    /**
     * 采集源标识（日志文件路径或 jar 包路径），作为事件的 source 字段。
     *
     * @return 采集源标识
     */
    String name();

    /**
     * 启动采集。重复调用幂等。
     */
    void start();

    /**
     * 停止采集并释放资源。重复调用幂等。
     */
    void stop();
}
