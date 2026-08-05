package com.smartops.infrastructure.mcp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * {@link McpClientConfig} 单元测试。
 *
 * <p>验证 MCP Client 配置类的初始化行为。对应 agent.md 阶段一任务3。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class McpClientConfigTest {

    @Test
    @DisplayName("logMcpClientStatus 方法执行不抛异常")
    void should_notThrowException_when_logMcpClientStatusCalled() {
        McpClientConfig config = new McpClientConfig();

        assertThatCode(() -> config.logMcpClientStatus())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("多次调用 logMcpClientStatus 均不抛异常")
    void should_notThrowException_when_calledMultipleTimes() {
        McpClientConfig config = new McpClientConfig();

        assertThatCode(() -> {
            config.logMcpClientStatus();
            config.logMcpClientStatus();
            config.logMcpClientStatus();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("McpClientConfig 可正常实例化")
    void should_instantiate_when_newCalled() {
        McpClientConfig config = new McpClientConfig();

        assertThat(config).isNotNull();
    }
}
