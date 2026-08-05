package com.smartops.infrastructure.observability;

import com.smartops.domain.audit.AuditEvent;
import com.smartops.domain.audit.port.AuditRecorder;
import com.smartops.infrastructure.persistence.audit.impl.AuditRepositoryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 异步审计记录器。
 *
 * <p>{@link AuditRecorder} 端口实现：业务线程仅向有界队列投递，
 * 单后台线程批量落库。队列满即丢弃并计数——按端口契约尽力投递，
 * 任何失败仅记日志，绝不抛出、绝不阻塞业务主链路。</p>
 *
 * <p>生命周期由配置类装配管理：{@link #start()} 启动消费线程，
 * {@link #stop()} 停止并 drain 队列剩余事件。</p>
 *
 * <p>线程安全：{@link #record} 可被多线程并发调用。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public class AsyncAuditRecorder implements AuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(AsyncAuditRecorder.class);

    /** 消费轮询超时（毫秒），兼顾停止响应与空转开销。 */
    private static final long POLL_TIMEOUT_MS = 200;

    private final AuditRepositoryImpl repository;
    private final ArrayBlockingQueue<AuditEvent> queue;
    private final ExecutorService consumer;
    private final AtomicLong droppedCount = new AtomicLong();

    private volatile boolean running;

    /**
     * 构造异步审计记录器。
     *
     * @param repository    审计持久化实现
     * @param queueCapacity 事件队列容量（背压边界，满则丢弃计数）
     */
    public AsyncAuditRecorder(AuditRepositoryImpl repository, int queueCapacity) {
        this.repository = repository;
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.consumer = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "audit-recorder");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 启动消费线程。
     */
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        consumer.execute(this::consumeLoop);
        log.info("审计记录器已启动，队列容量 {}", queue.remainingCapacity() + queue.size());
    }

    /**
     * 停止消费线程（drain 队列剩余事件后退出）。
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        consumer.shutdown();
        try {
            if (!consumer.awaitTermination(10, TimeUnit.SECONDS)) {
                consumer.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            consumer.shutdownNow();
        }
        log.info("审计记录器已停止，累计丢弃 {}", droppedCount.get());
    }

    @Override
    public void record(AuditEvent event) {
        if (event == null) {
            return;
        }
        try {
            if (!queue.offer(event)) {
                long dropped = droppedCount.incrementAndGet();
                if (dropped == 1 || dropped % 1000 == 0) {
                    log.warn("审计事件队列已满，丢弃事件（累计 {}）", dropped);
                }
            }
        } catch (RuntimeException e) {
            log.warn("审计事件投递失败: {}", e.toString());
        }
    }

    /**
     * 队列满被丢弃的事件总数（可观测指标）。
     *
     * @return 丢弃计数
     */
    public long getDroppedCount() {
        return droppedCount.get();
    }

    /**
     * 当前队列中待落库的事件数（可观测指标）。
     *
     * @return 队列深度
     */
    public long getQueueSize() {
        return queue.size();
    }

    /**
     * 消费主循环：取事件 → 落库；停止后 drain 队列。
     */
    private void consumeLoop() {
        while (running || !queue.isEmpty()) {
            try {
                AuditEvent event = queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (event != null) {
                    repository.save(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                log.error("审计事件落库失败: {}", e.toString());
            }
        }
    }
}
