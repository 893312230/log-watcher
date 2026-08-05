package com.smartops.agent.orchestrator;

import com.smartops.agent.worker.WorkerAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Worker Agent 自动注册器。
 *
 * <p>在 Spring 容器启动完成后，自动将所有 {@link WorkerAgent} Bean
 * 注册到 {@link TaskDispatcher}，建立角色到 Worker 的映射。</p>
 *
 * <p>线程安全：仅在应用启动时执行一次，无并发问题。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Component
public class WorkerRegistrar {

    private static final Logger log = LoggerFactory.getLogger(WorkerRegistrar.class);

    /** 任务分发器。 */
    private final TaskDispatcher dispatcher;

    /** 所有 Worker Agent Bean 列表（Spring 自动注入）。 */
    private final List<WorkerAgent> workers;

    /**
     * 构造 Worker 注册器。
     *
     * @param dispatcher 任务分发器
     * @param workers    所有 Worker Agent Bean
     */
    public WorkerRegistrar(TaskDispatcher dispatcher, List<WorkerAgent> workers) {
        this.dispatcher = dispatcher;
        this.workers = workers;
    }

    /**
     * 应用启动完成后自动注册所有 Worker。
     *
     * @param event 应用就绪事件
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerAllWorkers() {
        if (workers == null || workers.isEmpty()) {
            log.warn("未发现任何 Worker Agent Bean");
            return;
        }

        log.info("开始注册 Worker Agent，共 {} 个", workers.size());
        for (WorkerAgent worker : workers) {
            dispatcher.registerWorker(worker);
        }
        log.info("Worker Agent 注册完成，已注册 {} 个", dispatcher.workerCount());
    }
}
