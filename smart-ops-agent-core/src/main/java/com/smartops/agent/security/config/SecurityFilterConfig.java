package com.smartops.agent.security.config;

import com.smartops.agent.security.InputFilter;
import com.smartops.agent.security.impl.RegexInputFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * L0 输入过滤器装配（阶段五安全模型）。
 *
 * <p>默认激活，可通过 {@code smartops.security.l0.enabled=false} 关闭。
 * 关闭后无 {@link InputFilter} Bean，AgentController 跳过过滤。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(name = "smartops.security.l0.enabled", havingValue = "true",
        matchIfMissing = true)
public class SecurityFilterConfig {

    /**
     * 基于正则的 L0 输入过滤器 Bean。
     *
     * @return 输入过滤器实例
     */
    @Bean
    public InputFilter inputFilter() {
        return new RegexInputFilter();
    }
}
