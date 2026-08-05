# ADR-001：Spring AI 1.0.0 GA 的 starter 命名采用 model- 前缀

## 背景

阶段一搭建项目骨架时，最初根据旧版博客文章使用 `spring-ai-starter-openai` 作为 DeepSeek 接入的 starter 坐标。编译时报错：`'dependencies.dependency.version' for org.springframework.ai:spring-ai-starter-openai:jar is missing`，说明 Spring AI 1.0.0 GA 的 BOM 不管理这个坐标。

## 决策

查阅 Spring AI 1.0.0 官方文档（https://docs.spring.io/spring-ai/reference/1.0/api/chat/openai-chat.html）后确认：

- 1.0.0 GA 对 starter 命名做了调整，模型相关的 starter 统一加 `model-` 前缀
- OpenAI starter 正确坐标为 `spring-ai-starter-model-openai`
- MCP Client starter 保持 `spring-ai-starter-mcp-client`（不加 model- 前缀，因为 MCP 不是模型）
- ChatMemory 的 JDBC 实现为 `spring-ai-starter-model-chat-memory-repository-jdbc`

最终在 `smart-ops-bootstrap/pom.xml` 中使用 `spring-ai-starter-model-openai`。

## 备选方案

1. 使用旧版命名 `spring-ai-starter-openai` + 显式声明版本号：放弃 BOM 统一管理，违背 agent.md 第二章 2.3 节版本管理规范，放弃
2. 使用 Spring AI Alibaba 的 DeepSeek 专用 starter：引入额外依赖，且非 Spring 官方维护，放弃
3. 直接用 `spring-ai-openai`（非 starter）：失去自动配置能力，需手动配置 Bean，放弃

## 影响

- 所有 Spring AI starter 引用必须核对 1.0.0 GA 官方文档，不能照搬旧版博客
- 后续阶段引入 Embedding、VectorStore 等 starter 时，同样需要确认是否带 `model-` 前缀
- DeepSeek 通过 OpenAI 兼容协议接入，配置 `spring.ai.openai.base-url=https://api.deepseek.com` 即可，无需专用 starter
