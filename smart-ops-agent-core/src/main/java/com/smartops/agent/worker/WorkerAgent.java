package com.smartops.agent.worker;

import com.smartops.common.model.A2aRequest;
import com.smartops.common.model.A2aResponse;
import com.smartops.common.model.AgentCard;

/**
 * Worker Agent 接口。
 *
 * <p>阶段三 Multi-Agent 架构中所有专业子 Agent 的统一接口。
 * 每个 Worker 实现此接口，声明自己的能力卡片并处理 A2A 请求。</p>
 *
 * <p>实现类需在构造时向 {@link com.smartops.agent.a2a.AgentCardRegistry}
 * 注册自己的能力卡片。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public interface WorkerAgent {

    /**
     * 获取该 Agent 的能力卡片。
     *
     * @return Agent 能力卡片
     */
    AgentCard getCard();

    /**
     * 处理 A2A 请求，执行子任务。
     *
     * @param request A2A 请求
     * @return A2A 响应
     */
    A2aResponse handle(A2aRequest request);
}
