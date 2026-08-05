# ADR-009：迁移 Spring Boot 4.x + Spring AI 2.0.0，A2A 改用官方 SDK

## 背景

截至 2026-07，原技术栈（Spring Boot 3.4.5 + Spring AI 1.0.0）已落后一代以上：

- Spring Boot 3.5 / Spring Framework 6.2 已于 2026-06-30 OSS EOL，不再有免费安全补丁
- Spring AI 2.0.0 于 2026-06-12 GA（基线 Boot 4.x / Framework 7 / Java 17+），统一了 ToolCallingAdvisor、MCP 升级为 Java SDK 2.0（Streamable HTTP 取代 SSE 成为默认传输）
- 原规划"自研 gRPC A2A"的决策依据（"Spring AI 无原生 A2A 支持"）已过时：官方 a2a-java SDK（org.a2aproject.sdk，支持 JSON-RPC/gRPC/REST）与 spring-ai-a2a 社区项目（需 Spring AI 2.0）均已可用

## 决策

1. **框架直接迁移至 Spring Boot 4.0.7 + Spring AI 2.0.0**，不做 1.1.x 过渡（避免两次迁移成本）。
2. **A2A 跨 JVM 通信放弃自研 gRPC 方案**，未来需要时采用官方 a2a-java SDK + spring-ai-a2a；当前同 JVM Supervisor-Worker 架构保持不变。
3. MCP 传输从 SSE 切换为 Streamable HTTP，MCP 开关统一为 `smartops.mcp.enabled`（默认 false，无 MCP Server 时优雅降级）。

## 备选方案

- **停留 1.0.0 / 升级 1.1.7**：1.0.0 有已知 CVE（2026 年多个安全补丁仅回溯到 1.0.8 即止）；1.1.7 虽含补丁但 Boot 3.x 已 EOL，半年后仍需二次迁移，放弃。
- **自研 gRPC A2A 继续**：与官方协议演进脱节，维护成本高，且 spring-ai-a2a 已提供自动配置，放弃。

## 影响

- 配置键变更：`spring.ai.openai.chat.options.*` → `spring.ai.openai.chat.*`（options 段移除）；`spring.ai.mcp.client.sse.connections` → `spring.ai.mcp.client.streamable-http.connections`
- 行为变化：`MessageWindowChatMemory` 窗口淘汰按 User/Assistant 轮次对齐（maxMessages=5 实际保留 4 条），测试已按新语义更新
- 规划文档第六章"A2A 自研 gRPC"决策作废，由本 ADR 取代
- 环境要求：JDK 17+（当前使用 F:\jdk17\jdk-17.0.19+10）、Maven 3.8+
