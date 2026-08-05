# ADR-015：Embedding 服务选型——本地 Ollama bge-m3（OpenAI 兼容端点，独立配置命名空间）

## 背景

阶段四 RAG 两级检索的向量路需要 Embedding 模型。主 LLM DeepSeek **不提供 embedding API**，必须引入独立 embedding 服务。候选：SiliconFlow 等云端 OpenAI 兼容端点（BAAI/bge-m3，免费额度）、本地 Ollama 部署 bge-m3（`http://localhost:11434/v1` OpenAI 兼容）、其他商用 API。

## 决策

1. **本地 Ollama + bge-m3**：用户决策（2026-07-22）。理由：运维知识库可能含内网信息不宜出网；本地部署无调用费用、无外部可用性依赖；bge-m3 中英文混合检索效果与 1024 维输出满足运维知识库规模。
2. **独立配置命名空间 `smartops.embedding.*`**（base-url/api-key/model/dimensions），不复用聊天模型的 `spring.ai.openai.*`——两者 base-url、密钥、模型均不同。`EmbeddingConfig` 手工构造独立 `OpenAiEmbeddingModel`（Spring AI 2.0.0 构造签名实现时验证，参考 `OpenAiEmbeddingProperties.toOptions()`）。
3. **默认关闭**：`smartops.embedding.enabled=false`（`@ConditionalOnProperty`），无 Ollama 环境应用照常启动；维度默认 1024（bge-m3 实际输出），ETL 启动时校验与配置一致，不一致快速失败。
4. **不绑定 Ollama**：配置键为通用 OpenAI 兼容端点语义，未来切 SiliconFlow 等云端服务仅改配置。

## 备选方案

- **SiliconFlow 云端 bge-m3**：需外网与独立密钥，运维知识可能敏感，用户未选。
- **复用 `spring.ai.openai.embedding.*`**：与聊天模型共用 base-url/api-key 前缀，DeepSeek 与 Ollama 端点不同会互相干扰，放弃。
- **引入 spring-ai-ollama 专用 starter**：Ollama 自带 OpenAI 兼容端点，复用已有 openai 依赖即可，无需新增 starter。

## 影响

- 新增配置键 `smartops.embedding.enabled/base-url/api-key/model/dimensions`（application.yml 带中文注释）
- 本地开发需 `ollama pull bge-m3`；维度与 ES `dense_vector` mapping 必须一致（ADR-016 ETL 校验）
- 单元测试不触真实 Ollama：mock `EmbeddingModel` 接口
