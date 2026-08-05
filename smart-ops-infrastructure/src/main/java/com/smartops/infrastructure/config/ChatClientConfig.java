package com.smartops.infrastructure.config;

import com.smartops.infrastructure.advisor.BoundedToolExecutionEligibilityChecker;
import com.smartops.infrastructure.observability.Observability;
import com.smartops.infrastructure.observability.ObservedToolCallingManager;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * ChatClient 配置类。
 *
 * <p>对应 agent.md 阶段一任务2（接入 LLM）。
 * DeepSeek 兼容 OpenAI 协议，通过 application.yml 中的
 * spring.ai.openai.base-url 指向 DeepSeek，无需额外适配。</p>
 *
 * <p>本类负责：
 * <ol>
 *   <li>加载 ReAct 系统提示词（resources/prompts/react-system.txt）</li>
 *   <li>注入 MessageChatMemoryAdvisor，实现短期记忆自动管理</li>
 *   <li>构建全局默认 ChatClient Bean</li>
 * </ol></p>
 *
 * <p>线程安全：Bean 单例，ChatClient 本身线程安全。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Configuration
public class ChatClientConfig {

    /**
     * ReAct 系统提示词文件路径。
     * 文件顶部有注释说明用途，对应 agent.md 第五章 5.2 节注释规范。
     */
    @Value("classpath:prompts/react-system.txt")
    private Resource reactSystemPromptResource;

    /**
     * 单次请求内 LLM 工具调用的最大轮次上限（默认 10，对齐 ReActExecutor.MAX_ITERATIONS）。
     * 防止工具调用循环失控，对应配置项 smartops.react.max-tool-call-rounds。
     */
    @Value("${smartops.react.max-tool-call-rounds:10}")
    private int maxToolCallRounds;

    /** 工具调用最大并发数（≤0 表示不限）。 */
    @Value("${smartops.tool.max-concurrent:0}")
    private int toolMaxConcurrent;

    /** 工具调用并发许可获取超时毫秒。 */
    @Value("${smartops.tool.semaphore-timeout-ms:30000}")
    private long toolSemaphoreTimeoutMs;

    /**
     * 有界工具执行资格检查器 Bean。
     *
     * <p>Spring AI 2.0 的 ToolCallingAdvisor 未提供内置迭代上限配置，
     * 通过本检查器在应用层强制执行最大工具调用轮次（可被
     * ToolCallRoundGate 按请求覆盖，如 ReActExecutor 的 maxIterations）。</p>
     *
     * @return 有界资格检查器
     */
    @Bean
    public BoundedToolExecutionEligibilityChecker boundedToolExecutionEligibilityChecker() {
        return new BoundedToolExecutionEligibilityChecker(maxToolCallRounds);
    }

    /**
     * 带观测能力的工具调用管理器 Bean。
     *
     * <p>以 Spring AI 默认参数构建 {@link DefaultToolCallingManager} 作为委托，
     * 外层包装 {@link ObservedToolCallingManager} 统一记录工具调用指标与审计。
     * Observability Bean 缺失时透传不观测。</p>
     *
     * @param observabilityProvider 可观测性门面提供者
     * @return 工具调用管理器
     */
    @Bean
    public ToolCallingManager toolCallingManager(ObjectProvider<Observability> observabilityProvider) {
        return new ObservedToolCallingManager(
                DefaultToolCallingManager.builder().build(),
                observabilityProvider.getIfAvailable(),
                toolMaxConcurrent, toolSemaphoreTimeoutMs);
    }

    /**
     * 构建全局 ChatClient Bean。
     *
     * <p>配置默认系统提示词（ReAct 模式）、MessageChatMemoryAdvisor（短期记忆）
     * 与挂接有界资格检查器的 ToolCallingAdvisor（工具调用循环上限）。
     * 链中一旦存在 ToolAdvisor，Spring AI 不再自动注册默认 ToolCallingAdvisor，
     * 因此上限检查对所有经本客户端发起的工具调用生效。
     * conversationHistoryEnabled 置 false：会话历史由 MessageChatMemoryAdvisor 管理，
     * 避免工具循环内部重复拼接历史。</p>
     *
     * @param builder            Spring Boot 自动配置的 ChatClient.Builder
     * @param chatMemory         短期记忆，由 {@link ChatMemoryConfig} 提供
     * @param eligibilityChecker 有界工具执行资格检查器
     * @param toolCallingManager 带观测能力的工具调用管理器
     * @return 配置好的 ChatClient 实例
     * @throws IOException 当读取系统提示词文件失败时抛出
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory,
                                 BoundedToolExecutionEligibilityChecker eligibilityChecker,
                                 ToolCallingManager toolCallingManager) throws IOException {
        String systemPrompt = reactSystemPromptResource.getContentAsString(StandardCharsets.UTF_8);

        ToolCallingAdvisor toolCallingAdvisor = ToolCallingAdvisor.builder()
                .toolCallingManager(toolCallingManager)
                .toolExecutionEligibilityChecker(eligibilityChecker)
                .conversationHistoryEnabled(false)
                .build();

        return builder
                .defaultSystem(systemPrompt)
                // 记忆 Advisor：每次调用自动加载/保存会话历史；
                // 工具 Advisor：编排工具调用循环并强制执行轮次上限
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        toolCallingAdvisor
                )
                .build();
    }
}
