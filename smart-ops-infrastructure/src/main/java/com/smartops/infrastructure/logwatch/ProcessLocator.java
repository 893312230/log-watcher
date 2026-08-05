package com.smartops.infrastructure.logwatch;

import java.nio.file.Path;
import java.util.Optional;

/**
 * 进程日志定位器。
 *
 * <p>按 jar 包路径定位运行中 java 进程及其 stdout 日志文件，
 * 实现位于 impl 子包（{@code JpsProcessLocator}）。
 * 全部为只读探测，不涉及进程操作，因此不过 SecurityGate（见 ADR）。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public interface ProcessLocator {

    /**
     * 定位指定 jar 包对应进程的 stdout 日志文件。
     *
     * @param jarPath jar 包路径（与进程启动参数中的路径匹配）
     * @return 日志文件路径；进程不存在或 stdout 非文件时返回 empty
     */
    Optional<Path> locateLogFile(String jarPath);
}
