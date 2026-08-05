package com.smartops.infrastructure.llm;

import org.springframework.ai.chat.client.ChatClient;

/**
 * 大模型 Provider 接口（阶段五多模型抽象层）。
 *
 * <p>屏蔽不同模型/端点的差异（温度约束、工具支持、API 地址）。
 * 每个实现代表一个可用的 LLM 端点，由 {@link LlmProviderRegistry} 统一管理，
 * 上层通过名称或能力标志选择 Provider。</p>
 *
 * <p>当前仅实现 OpenAI 兼容类型（{@code type="openai"}），
 * 接口保留 Anthropic 等扩展空间。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public interface LlmProvider {

    /** Provider 唯一名称（如 deepseek、kimi-coding、moonshot）。 */
    String name();

    /** Provider 类型（当前仅 openai，后续可扩展 anthropic 等）。 */
    String type();

    /** 是否支持工具调用（Function Calling / Tool Use）。 */
    boolean supportsTools();

    /** 该端点支持的最低采样温度。 */
    double minTemperature();

    /** 该端点支持的最高采样温度。 */
    double maxTemperature();

    /**
     * 返回当前 Provider 对应的 {@link ChatClient}。
     *
     * <p>ChatClient 为纯对话客户端（无默认 Advisor），
     * 记忆、工具调用等 Advisor 由 ChatService 按调用场景附加。</p>
     *
     * @return 该 Provider 的 ChatClient 实例
     */
    ChatClient chatClient();
}
