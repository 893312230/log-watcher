package com.smartops.agent.orchestrator;

import com.smartops.agent.worker.WorkerAgent;
import com.smartops.common.enums.AgentRole;
import com.smartops.common.exception.AgentException;
import com.smartops.common.model.A2aRequest;
import com.smartops.common.model.A2aResponse;
import com.smartops.common.model.AgentCard;
import com.smartops.common.model.SubTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务分发器。
 *
 * <p>对应 agent.md 阶段三特性4。负责将 Supervisor 分解的子任务路由到
 * 对应的 Worker Agent 执行。基于运行时注册的 Worker 索引，
 * 构造 A2A 请求并调用 Worker 处理。</p>
 *
 * <p><b>分发策略</b>：
 * <ol>
 *   <li>根据子任务的 {@link SubTask#targetRole()} 查找已注册的 Worker</li>
 *   <li>构造 {@link A2aRequest} 并调用 {@link WorkerAgent#handle}</li>
 *   <li>未找到 Worker 时返回失败响应</li>
 * </ol></p>
 *
 * <p><b>注册约束</b>：同一角色只允许注册一个 Worker，重复注册不同实例
 * 抛出 {@link AgentException}（静默覆盖会让运维操作路由到非预期实现）。</p>
 *
 * <p>线程安全：内部使用 {@link ConcurrentHashMap} 维护 Worker 索引，
 * 支持并发注册与分发。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Component
public class TaskDispatcher {

    private static final Logger log = LoggerFactory.getLogger(TaskDispatcher.class);

    /** 按角色索引的 Worker 映射（运行时动态维护）。 */
    private final Map<AgentRole, WorkerAgent> workersByRole = new ConcurrentHashMap<>();

    /**
     * 注册 Worker Agent。
     *
     * <p>Worker 在 Spring 容器初始化时调用此方法注册自己。
     * 同一实例重复注册幂等；同一角色注册不同实例时抛出异常。</p>
     *
     * @param worker Worker Agent 实例
     * @throws NullPointerException 当 worker 为 null 时
     * @throws AgentException       当同角色已注册不同 Worker 实例时
     */
    public void registerWorker(WorkerAgent worker) {
        Objects.requireNonNull(worker, "worker 不能为 null");
        AgentCard card = worker.getCard();
        WorkerAgent existing = workersByRole.putIfAbsent(card.role(), worker);
        if (existing != null && existing != worker) {
            throw new AgentException("WORKER_DUPLICATE_ROLE",
                    String.format("角色 %s 已注册 Worker %s，拒绝重复注册 %s",
                            card.role(), existing.getCard().agentId(), card.agentId()));
        }
        log.info("Worker 注册到分发器: agentId={}, role={}", card.agentId(), card.role());
    }

    /**
     * 分发子任务到对应的 Worker 执行。
     *
     * @param subTask 子任务
     * @return A2A 响应
     */
    public A2aResponse dispatch(SubTask subTask) {
        Objects.requireNonNull(subTask, "subTask 不能为 null");

        log.info("分发子任务: taskId={}, targetRole={}, priority={}",
                subTask.taskId(), subTask.targetRole(), subTask.priority());

        WorkerAgent worker = workersByRole.get(subTask.targetRole());
        if (worker == null) {
            String error = String.format("未找到角色为 %s 的 Worker", subTask.targetRole());
            log.warn(error);
            return A2aResponse.failure(
                    UUID.randomUUID().toString(),
                    subTask.taskId(),
                    subTask.targetRole(),
                    error);
        }

        // 构造 A2A 请求
        A2aRequest request = new A2aRequest(
                UUID.randomUUID().toString(),
                subTask.taskId(),
                AgentRole.SUPERVISOR,
                subTask.targetRole(),
                subTask.instruction(),
                subTask.parentTaskId()
        );

        // 调用 Worker 处理
        return worker.handle(request);
    }

    /**
     * 获取已注册的 Worker 数量。
     *
     * @return Worker 数量
     */
    public int workerCount() {
        return workersByRole.size();
    }

    /**
     * 判断指定角色是否有已注册的 Worker。
     *
     * @param role Agent 角色
     * @return 如果有返回 true
     */
    public boolean hasWorker(AgentRole role) {
        return role != null && workersByRole.containsKey(role);
    }
}
