package com.smartops.infrastructure.llm;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link LlmProviderRegistry} 的内存实现。
 *
 * <p>构造时接收全部 Provider 与默认名称，按注册顺序维护。
 * 线程安全：构造后不可变，因此读操作无需加锁。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public class LlmProviderRegistryImpl implements LlmProviderRegistry {

    private final Map<String, LlmProvider> providers;
    private final LlmProvider defaultProvider;
    private final List<LlmProvider> providerList;

    /**
     * 构造 Provider 注册表。
     *
     * @param providers      全部 Provider（注册顺序）
     * @param defaultName    默认 Provider 名称
     * @throws IllegalArgumentException 当 providers 为空时
     * @throws IllegalArgumentException 当 defaultName 对应的 Provider 不存在时
     */
    public LlmProviderRegistryImpl(List<LlmProvider> providers, String defaultName) {
        if (providers.isEmpty()) {
            throw new IllegalArgumentException("至少需要注册一个 LLM Provider");
        }
        Map<String, LlmProvider> map = new LinkedHashMap<>();
        for (LlmProvider p : providers) {
            map.put(p.name(), p);
        }
        this.providers = Collections.unmodifiableMap(map);
        this.providerList = Collections.unmodifiableList(List.copyOf(providers));
        this.defaultProvider = Optional.ofNullable(map.get(defaultName))
                .orElseThrow(() -> new IllegalArgumentException(
                        "默认 Provider '" + defaultName + "' 不在已注册的 Provider 中: "
                                + map.keySet()));
    }

    @Override
    public Optional<LlmProvider> getByName(String name) {
        return Optional.ofNullable(providers.get(name));
    }

    @Override
    public LlmProvider getDefault() {
        return defaultProvider;
    }

    @Override
    public Optional<LlmProvider> getToolCapable() {
        return providers.values().stream()
                .filter(LlmProvider::supportsTools)
                .findFirst();
    }

    @Override
    public List<LlmProvider> all() {
        return providerList;
    }
}
