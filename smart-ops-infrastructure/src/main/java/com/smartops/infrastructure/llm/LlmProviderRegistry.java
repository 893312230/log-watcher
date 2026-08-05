package com.smartops.infrastructure.llm;

import java.util.List;
import java.util.Optional;

/**
 * LLM Provider 注册表（阶段五多模型抽象层）。
 *
 * <p>管理全部 {@link LlmProvider} 实例，支持按名称获取、按能力选择。
 * "默认" Provider 由配置 {@code smartops.llm.default-provider} 指定。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public interface LlmProviderRegistry {

    /**
     * 按名称获取 Provider。
     *
     * @param name Provider 名称
     * @return 命中的 Provider，不存在时 empty
     */
    Optional<LlmProvider> getByName(String name);

    /**
     * 获取默认 Provider。
     *
     * @return 配置指定的默认 Provider
     */
    LlmProvider getDefault();

    /**
     * 获取首个 {@link LlmProvider#supportsTools()} 为 true 的 Provider。
     *
     * @return 支持工具调用的 Provider，不存在时 empty（此时调用方应降级为纯文本对话）
     */
    Optional<LlmProvider> getToolCapable();

    /**
     * 返回所有已注册 Provider（不可变列表）。
     *
     * @return Provider 列表
     */
    List<LlmProvider> all();
}
