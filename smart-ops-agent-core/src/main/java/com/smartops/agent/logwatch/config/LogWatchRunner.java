package com.smartops.agent.logwatch.config;

import com.smartops.agent.logwatch.AlertPipelineService;
import com.smartops.infrastructure.logwatch.LogSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.util.List;

/**
 * 日志采集生命周期托管器。
 *
 * <p>应用就绪后启动分析管线与全部采集源；容器关闭时逆序停止
 * （先停采集源不再产生事件，再停管线 drain 队列剩余事件）。</p>
 *
 * <p>线程安全：仅在容器启动/关闭钩子中单线程调用。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public class LogWatchRunner implements ApplicationRunner, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LogWatchRunner.class);

    private final List<LogSource> sources;
    private final AlertPipelineService pipeline;

    /**
     * 构造生命周期托管器。
     *
     * @param sources  采集源列表
     * @param pipeline 分析管线服务
     */
    public LogWatchRunner(List<LogSource> sources, AlertPipelineService pipeline) {
        this.sources = List.copyOf(sources);
        this.pipeline = pipeline;
    }

    @Override
    public void run(ApplicationArguments args) {
        pipeline.start();
        for (LogSource source : sources) {
            source.start();
        }
        log.info("日志采集分析已启动：{} 个采集源", sources.size());
    }

    @Override
    public void close() {
        for (LogSource source : sources) {
            try {
                source.stop();
            } catch (RuntimeException e) {
                log.warn("采集源停止异常: {}", e.toString());
            }
        }
        pipeline.stop();
        log.info("日志采集分析已停止");
    }
}
