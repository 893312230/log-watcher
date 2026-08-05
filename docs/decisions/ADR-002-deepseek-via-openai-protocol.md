# ADR-002：DeepSeek 通过 OpenAI 兼容协议接入

## 背景

agent.md 第九章决策1要求"优先使用国内可用的模型（通义千问/DeepSeek）"。需要确定 DeepSeek 的具体接入方式。

## 决策

使用 Spring AI 的 `spring-ai-starter-model-openai` starter，通过配置 `spring.ai.openai.base-url=https://api.deepseek.com` 将请求转发到 DeepSeek，复用 OpenAI 协议。

## 备选方案

1. Spring AI Alibaba 的 DeepSeek 专用 starter：非 Spring 官方维护，增加依赖复杂度，放弃
2. 通义千问：需引入 `spring-ai-alibaba`，生态独立，与 Spring AI BOM 管理不一致，放弃
3. 自研 HTTP 客户端直接调用 DeepSeek API：失去 Spring AI 的 ChatClient/Advisor/ToolCalling 等抽象能力，放弃

## 影响

- DeepSeek API Key 通过环境变量 `DEEPSEEK_API_KEY` 注入，不硬编码
- 默认模型 `deepseek-chat`（成本较低），复杂推理场景可切换 `deepseek-reasoner`
- 预留 OpenAI 兼容接口，未来可故障切换到其他兼容 OpenAI 协议的模型
