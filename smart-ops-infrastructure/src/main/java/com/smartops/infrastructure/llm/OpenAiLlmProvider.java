package com.smartops.infrastructure.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * OpenAI 兼容协议的 {@link LlmProvider} 实现（阶段五多模型抽象层）。
 *
 * <p>通过 {@link OpenAiChatModel} 按配置的 base-url/api-key/model/temperature
 * 构建 {@link ChatClient}。每个 Provider 实例对应一个独立的 ChatModel + ChatClient。
 * 线程安全：ChatClient 与 ChatModel 均线程安全。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public class OpenAiLlmProvider implements LlmProvider {

    private final String name;
    private final boolean supportsTools;
    private final double minTemperature;
    private final double maxTemperature;
    private final ChatClient chatClient;

    /**
     * 构造 OpenAI 兼容 Provider。
     *
     * @param name           Provider 名称
     * @param baseUrl        API 端点地址
     * @param apiKey         API 密钥
     * @param model          模型名
     * @param temperature    默认采样温度
     * @param supportsTools  是否支持工具调用
     * @param minTemperature 最低温度
     * @param maxTemperature 最高温度
     */
    public OpenAiLlmProvider(String name, String baseUrl, String apiKey, String model,
                              double temperature, boolean supportsTools,
                              double minTemperature, double maxTemperature) {
        this.name = name;
        this.supportsTools = supportsTools;
        this.minTemperature = minTemperature;
        this.maxTemperature = maxTemperature;
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .model(model)
                .temperature(temperature)
                .build();
        this.chatClient = ChatClient.builder(OpenAiChatModel.builder()
                .options(options).build()).build();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String type() {
        return "openai";
    }

    @Override
    public boolean supportsTools() {
        return supportsTools;
    }

    @Override
    public double minTemperature() {
        return minTemperature;
    }

    @Override
    public double maxTemperature() {
        return maxTemperature;
    }

    @Override
    public ChatClient chatClient() {
        return chatClient;
    }
}
