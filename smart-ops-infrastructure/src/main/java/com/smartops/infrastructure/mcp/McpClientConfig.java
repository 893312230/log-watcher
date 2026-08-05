package com.smartops.infrastructure.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * MCP Client 配置类。
 *
 * <p>对应 agent.md 阶段一任务3（实现 MCP Client 端）。
 * Spring AI 的 spring-ai-starter-mcp-client 提供自动配置，
 * 本类仅做启动状态记录与条件化控制。</p>
 *
 * <p><b>配置位置</b>：MCP Client 的连接配置在 application.yml 的
 * spring.ai.mcp.client.streamable-http.connections.prometheus 下（Spring AI 2.0 起
 * Streamable HTTP 取代 SSE 作为默认传输）。
 * Spring AI 自动读取并创建 McpSyncClient Bean。</p>
 *
 * <p><b>条件化启用</b>：通过 smartops.mcp.enabled 属性控制是否启用 MCP，
 * 该属性同时驱动 spring.ai.mcp.client.enabled（见 application.yml）。
 * 当 Prometheus MCP Server 不可用时保持 false，PrometheusTools 返回显式不可用响应。</p>
 *
 * <p>线程安全：Configuration 类，Bean 单例。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(prefix = "smartops.mcp", name = "enabled", havingValue = "true")
public class McpClientConfig {

    private static final Logger log = LoggerFactory.getLogger(McpClientConfig.class);

    /**
     * 启动时记录 MCP Client 初始化状态。
     *
     * <p>Spring AI 的自动配置会在此前完成 McpSyncClient Bean 的创建。
     * 本方法仅做日志记录，便于排查 MCP 连接问题。</p>
     */
    @PostConstruct
    public void logMcpClientStatus() {
        log.info("MCP Client 已启用，Prometheus MCP Server 连接配置见 application.yml 的 spring.ai.mcp.client.sse.connections");
    }
}
