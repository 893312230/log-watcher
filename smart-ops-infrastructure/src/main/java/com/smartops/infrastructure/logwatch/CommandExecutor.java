package com.smartops.infrastructure.logwatch;

import java.io.IOException;
import java.util.List;

/**
 * 外部命令执行器。
 *
 * <p>抽象 jps 等外部命令的执行，便于测试注入替身，
 * 避免单测依赖真实 JDK 工具链。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@FunctionalInterface
public interface CommandExecutor {

    /**
     * 执行命令并返回标准输出的全部行。
     *
     * @param command 命令及参数
     * @return 输出行列表
     * @throws IOException          命令不存在或执行失败
     * @throws InterruptedException 等待被中断
     */
    List<String> exec(String... command) throws IOException, InterruptedException;
}
