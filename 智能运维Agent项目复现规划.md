# 智能运维 Agent 项目 —— 复现技术分析与实施计划

## 项目概述

该项目是一个基于 Spring Boot + Spring AI 构建的 Multi-Agent 智能运维平台，由一位电子科技大学硕士从 Java 后端转型 AI Agent 开发所构建。核心能力包括：通过自然语言查询运维指标、自动化故障分析定位、智能告警处理、运维知识库问答等。

项目的核心创新点在于将传统 Spring 生态的工程化能力与 AI Agent 技术深度融合，涵盖意图识别、智能路由、Multi-Agent 编排、多层记忆、混合检索 RAG、MCP 工具集成、安全控制、A2A 通信等完整能力。

---

## 一、技术栈详细分析

### 1.1 Spring AI 核心能力

**版本选择：Spring AI 2.0.0 + Spring Boot 4.x（2026 修订）**

> 原规划锁定 Spring AI 1.0.0（2025-05 GA）。审查时 Spring AI 2.0.0 已 GA 且需 Boot 4.x，Boot 3.5 已停止维护，故直接迁移（见 `docs/decisions/ADR-009-boot4-spring-ai-2-migration.md`）。核心能力对应关系不变，MCP 传输由 HTTP SSE 升级为 Streamable HTTP。

Spring AI 的核心能力如下：

| 能力模块 | 功能描述 | 在本项目中的复用 |
|---|---|---|
| `ChatClient` | 统一 AI 模型交互接口，支持 20+ 模型 | 所有 Agent 与 LLM 交互的入口 |
| `Advisor` 拦截器链 | 在 Prompt 执行前后注入检索数据、对话记忆、安全控制 | 实现记忆注入、安全过滤、上下文增强 |
| `@Tool` 注解 | 声明式工具定义，自动转换为 Tool Calling 格式 | 所有运维工具（Prometheus 查询、MySQL 查询等） |
| `ChatMemory` | 对话记忆管理，支持 `MessageWindowChatMemory`、`VectorStoreChatMemory` | 多层记忆体系的基础抽象 |
| `VectorStore` 抽象 | 可移植向量存储，支持 20+ 向量数据库 | RAG 检索的向量存储层 |
| RAG Pipeline | `QuestionAnswerAdvisor`、`RetrievalAugmentationAdvisor` | 知识库检索增强 |
| ETL 框架 | 轻量级文档读取、分块、Embedding 生成流水线 | 运维知识库的数据导入 |
| MCP 支持 | `spring-ai-starter-mcp-client` / `spring-ai-starter-mcp-server` | 连接 Prometheus、MySQL 等外部工具 |
| Agent 工作流模式 | Chain、Routing、Orchestrator-Workers、Parallelization | Multi-Agent 编排的底层模式 |
| 可观测性 | Micrometer 集成，Token 用量、延迟、工具调用追踪 | 运维监控与成本分析 |

**选型理由**：Spring AI 是 Java 生态中 AI 集成的事实标准。相比 LangChain4j，Spring AI 与 Spring Boot 生态的集成更加自然，且官方维护的 MCP Java SDK 就是以 Spring AI 的代码为基础捐赠给 Anthropic 的。1.0 GA 的稳定 API 承诺对企业级项目至关重要。

### 1.2 MCP 协议在 Java 生态的集成方式

MCP（Model Context Protocol）由 Anthropic 于 2024 年 11 月推出，定义了 AI 模型与外部工具/数据源之间的标准化通信协议。

**架构示意**：

```
┌─────────────────────────────────────────────────┐
│                  Spring AI App                    │
│  ┌──────────────────┐  ┌──────────────────────┐  │
│  │  MCP Client       │  │  MCP Server          │  │
│  │  (spring-ai-      │  │  (spring-ai-         │  │
│  │   starter-mcp-    │  │   starter-mcp-       │  │
│  │   client)         │  │   server)            │  │
│  └────────┬─────────┘  └──────────┬───────────┘  │
└───────────┼───────────────────────┼──────────────┘
            │  Transport:           │
            │  - stdio (进程)        │
            │  - HTTP SSE (远程)     │
            ▼                       ▼
    ┌──────────────┐      ┌──────────────┐
    │ Prometheus   │      │   MySQL      │
    │ MCP Server   │      │ MCP Server   │
    └──────────────┘      └──────────────┘
```

**本项目中的集成策略**：
- MCP Client 端（主应用）：通过 `spring-ai-starter-mcp-client` 连接外部 MCP Server
- MCP Server 端：为 Prometheus、MySQL、ELK 等运维组件分别构建 MCP Server
- 传输方式：内部服务使用 stdio 传输，外部服务使用 HTTP SSE 传输
- 安全控制：通过 Spring Security + OAuth2 实现 MCP 工具的鉴权访问

### 1.3 ReAct 与 Plan-and-Solve 两种 Agent 模式

| 维度 | ReAct（推理+行动） | Plan-and-Solve（先规划后执行） |
|---|---|---|
| **核心思想** | 边想边做，交替进行推理和行动 | 先生成完整计划，再逐步执行 |
| **执行流程** | Thought → Action → Observation → 循环 | Planner → Executor → Replanner → Summarizer |
| **适用场景** | 实时告警分析、故障排查、交互式查询 | 多步骤依赖、自动化运维流程编排 |
| **优势** | 灵活、即时反馈、适合探索性任务 | 全局视角、可预测、适合结构化任务 |
| **劣势** | 可能陷入循环、缺乏全局规划 | 计划可能不准确、执行成本高 |
| **Spring AI 实现** | `ChatClient` + Tool Calling + Advisor 链 | 自定义 Orchestrator-Workers 模式 |

**动态路由策略**是项目的核心创新点 —— 根据任务特征（步骤数预估、依赖关系、实时性要求、历史成功率）动态选择执行模式。

### 1.4 A2A Agent 间通信

A2A（Agent-to-Agent）协议由 Google Cloud 于 2025 年推出，定义了不同 AI Agent 之间的标准化通信方式。

> **2026 修订**：原规划因 Spring AI 无原生 A2A 支持而自研 gRPC 通信层。审查时官方 `a2a-java` SDK 与 `spring-ai-a2a` 集成已存在，跨 JVM A2A 通信改为基于官方 SDK 实现（属阶段三后续/阶段五范围，见 ADR-009）。当前实现为 JVM 内 Supervisor-Worker 通信，接口设计已向官方 A2A 模型（Agent Card / Task）对齐。

### 1.5 混合检索（BM25 + 向量检索，Rerank 可选）

**生产环境标准范式（2026 修订后）**：Sparse (BM25) + Dense (Vector) + RRF 融合；Cross-Encoder Rerank 降级为可选增强（见 `docs/decisions/ADR-013-phase4-simplification.md`）

```
用户查询
    │
    ├──▶ BM25 检索 (Elasticsearch/Lucene) → Top-K 候选集 A
    ├──▶ 向量检索 (Elasticsearch dense_vector) → Top-K 候选集 B
    └──▶ 结果融合 (RRF 算法) → 最终 Top-N
              （可选：Cross-Encoder Rerank，仅当离线评测证明召回不足时引入）
```

**推荐方案**：Elasticsearch 8.x。理由：ES 原生支持 dense_vector + BM25 混合查询，运维团队通常已有 ES 运维经验；独立 Rerank 服务（bge-reranker-v2-m3）非阶段四交付项。

---

## 二、项目模块划分

### 2.1 整体分层架构

```
┌─────────────────────────────────────────────────────────────────┐
│                      Presentation Layer                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐   │
│  │  REST API    │  │   SSE Push   │  │  Web Admin Console   │   │
│  └──────────────┘  └──────────────┘  └──────────────────────┘   │
├─────────────────────────────────────────────────────────────────┤
│                      Application Layer                           │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐          │
│  │ Intent   │ │  Agent   │ │  Task    │ │ Session  │          │
│  │ Analyzer │ │  Router  │ │ Scheduler│ │ Manager  │          │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘          │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐          │
│  │ Monitor  │ │ Analyze  │ │ Execute  │ │Knowledge │          │
│  │  Agent   │ │  Agent   │ │  Agent   │ │  Agent   │          │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘          │
├─────────────────────────────────────────────────────────────────┤
│                     Infrastructure Layer                         │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐          │
│  │  Memory  │ │   RAG    │ │   MCP    │ │   A2A    │          │
│  │  Manager │ │  Engine  │ │  Gateway │ │  Comm    │          │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘          │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐          │
│  │ Security │ │  Rate    │ │  Task    │ │Observab- │          │
│  │  Guard   │ │ Limiter  │ │Persister │ │ ility    │          │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘          │
├─────────────────────────────────────────────────────────────────┤
│                         Data Layer                               │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐          │
│  │  MySQL   │ │  Redis   │ │ Elastic  │ │PGVector  │          │
│  │(业务数据) │ │(缓存/会话)│ │ search   │ │(向量存储) │          │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘          │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 核心模块职责

| 模块 | 职责 | 核心组件 |
|---|---|---|
| **intent-analyzer** | 四层意图识别 + 冲突解决 | `IntentPipeline`, `RegexMatcher`, `ActionWordCounter`, `MlClassifier`, `LlmFallback`, `ConflictResolver` |
| **agent-router** | 动态选择 ReAct / Plan-and-Solve | `AgentRouter`, `TaskAnalyzer`, `ReActStrategy`, `PlanAndSolveStrategy` |
| **agent-orchestrator** | Supervisor-Worker 调度中心 | `SupervisorAgent`, `WorkerRegistry`, `TaskDispatcher` |
| **agent-pool** | 各子 Agent 实现 | `MonitorAgent`, `AnalyzeAgent`, `ExecuteAgent`, `KnowledgeAgent` |
| **plan-executor** | Plan-and-Solve 计划生成/校验/重试 | `PlanGenerator`, `PlanValidator`, `StepExecutor`, `Replanner` |
| **memory-manager** | 多层记忆体系 | `ShortTermMemory`(滑动窗口), `MediumTermMemory`(摘要压缩), `LongTermMemory`(Redis), `WorkingMemory`(上下文) |
| **rag-engine** | BM25+向量检索+Rerank | `HybridRetriever`, `Bm25Retriever`, `VectorRetriever`, `RerankService`, `RrfFusion` |
| **mcp-gateway** | MCP 协议工具集成 | `McpClientManager`, `ToolRegistry`, `ToolExecutionLog` |
| **security-guard** | 四级安全控制 + Prompt 注入防护 | `L0Filter`~`L3Filter`, `InjectionDetector` |
| **a2a-communication** | Agent 间通信 | `AgentCardRegistry`, `A2aClient`, `A2aServer`, `TaskManager` |
| **sse-push** | SSE 实时推送 + 心跳 | `SseEmitterManager`, `TaskStatusPublisher`, `HeartbeatDetector` |
| **concurrency-control** | 并发控制 + 限流 | `RateLimiter`, `ConcurrencyController`, `TaskQueue` |

---

## 三、复现路线图（分 5 阶段）

### 阶段一：核心骨架 + 单 Agent MVP（优先级最高）

**目标**：跑通 Spring AI + MCP 的基础链路，实现一个能通过自然语言查询 Prometheus 指标的简单 Agent。

**技术工作**：

| 序号 | 任务 | 详情 |
|---|---|---|
| 1 | 搭建 Spring Boot 3.x + Spring AI 1.0.0 基础项目 | 多模块 Maven 项目，统一 BOM 管理 |
| 2 | 接入 LLM | 推荐通义千问或 DeepSeek（国内可用，成本低） |
| 3 | 实现 MCP Client 端 | 连接 Prometheus MCP Server |
| 4 | 实现基础 Tool Calling | `ChatClient` + `@Tool` 注解 |
| 5 | 实现短期记忆 | `MessageWindowChatMemory`（滑动窗口 20 条） |
| 6 | 实现 REST API + SSE 推送 | 基础对话接口 + 流式输出 |
| 7 | 搭建 MySQL + Redis | 业务数据 + 缓存/会话 |

**产出**：一个可对话的运维查询助手，支持自然语言查询指标。代码量约 2000-3000 行。

### 阶段二：意图识别 + 智能路由（核心差异化）

**目标**：实现四层意图识别和 ReAct / Plan-and-Solve 动态路由。

**技术工作**：

| 序号 | 任务 | 详情 |
|---|---|---|
| 1 | L1 正则规则 | 预定义运维场景规则匹配 |
| 2 | L2 动作词统计 | 基于关键词频率与权重 |
| 3 | ~~L3 ML 分类器~~（2026 修订：已移除） | 原规划 FastText 冷启动；无标注数据难落地，意图识别定型为 L1/L2/L4 三层（ADR-011、ADR-013） |
| 4 | L4 LLM 兜底 | 低置信度时由 LLM 分类（返回真实置信度 JSON） |
| 5 | 冲突解决器 | 加权投票或置信度比较 |
| 6 | 任务复杂度分析器 | 步骤数预估、依赖关系判断 |
| 7 | ReAct 模式实现 | 基于 Spring AI Tool Calling 循环 |
| 8 | Plan-and-Solve 实现 | Planner + Executor + Validator + Replanner |
| 9 | 路由决策引擎 | 根据任务特征动态选择模式 |

**产出**：能根据任务复杂度自动选择执行模式的 Agent。代码量约 4000-6000 行。

### 阶段三：Multi-Agent 架构 + A2A 通信

**目标**：实现 Supervisor-Worker 多 Agent 架构和 Agent 间通信。

**技术工作**：

| 序号 | 任务 | 详情 |
|---|---|---|
| 1 | Supervisor Agent | 任务分解、Worker 分配、结果聚合 |
| 2 | Monitor Agent | 实时监控、告警查询、指标趋势分析 |
| 3 | Analyze Agent | 根因分析、日志分析、异常检测 |
| 4 | Execute Agent | 自动化运维操作（重启、扩缩容、配置变更） |
| 5 | Knowledge Agent | 运维知识库问答、最佳实践推荐 |
| 6 | Agent Card 注册发现 | Agent 能力描述与注册 |
| 7 | A2A 通信层 | 当前为 JVM 内通信；跨 JVM 时采用官方 a2a-java SDK + spring-ai-a2a（2026 修订，替代自研 gRPC） |
| 8 | 任务状态异步通知 | 流式结果传输 |

**产出**：多 Agent 协作的智能运维平台。代码量约 5000-7000 行。

### 阶段四：记忆系统 + 混合检索 RAG（体验优化）

**目标**：实现多层记忆体系和混合检索 RAG 知识库。

**技术工作**：

| 序号 | 任务 | 详情 |
|---|---|---|
| 1 | 短期记忆 | `MessageWindowChatMemory`（滑动窗口，默认 20 条） |
| 2 | 中期记忆 | 会话摘要压缩（定期对历史对话做 LLM 摘要） |
| 3 | 长期记忆 | Redis 持久化 + 用户画像存储 |
| 4 | 工作记忆 | 当前任务上下文（工具调用结果、中间状态） |
| 5 | Elasticsearch 部署 | 索引设计与运维知识库构建 |
| 6 | BM25 检索器 | 基于 Lucene |
| 7 | 向量检索器 | 基于 dense_vector 字段 |
| 8 | RRF 融合算法 | 结果融合 |
| 9 | ~~Cross-Encoder Rerank~~（2026 修订：降级为可选） | 仅当离线评测证明召回不足时引入（ADR-013） |
| 10 | 知识库 ETL 流水线 | 文档读取、分块、Embedding 生成 |

**产出**：具备长期记忆和智能知识检索的 Agent。代码量约 3000-4000 行。

### 阶段五：安全控制 + 生产化（加固与上线）

**目标**：四级安全控制、Prompt 注入防护、并发控制、完整可观测性。

**技术工作**：

| 序号 | 任务 | 详情 |
|---|---|---|
| 1 | L0 输入过滤 | SQL 注入、XSS、命令注入等传统安全过滤 |
| 2 | L1 权限校验 | 基于 RBAC 的工具调用权限控制 |
| 3 | L2 操作审计 | 所有工具调用和 LLM 输出的完整审计日志 |
| 4 | L3 人工确认 | 高风险操作（如重启服务）需人工确认。最小版本（SecurityGate + 一次性确认令牌）已于 2026 修复期提前落地（ADR-010），阶段五补齐 RBAC 权限模型 |
| 5 | Prompt 注入防护 | 输入清洗 + 指令边界隔离 + 检测模型 + 输出过滤 |
| 6 | LLM 调用限流 | 基于 Token Bucket |
| 7 | 工具调用并发控制 | 基于 Semaphore |
| 8 | 任务队列 | 有界队列 + 拒绝策略 |
| 9 | 熔断降级 | Resilience4j |
| 10 | 可观测性 | Micrometer 指标 + 分布式追踪 + 任务状态持久化 |
| 11 | 心跳检测 + 连接管理 | SSE 断线重连 + 任务恢复 |

**产出**：生产可用的智能运维平台。代码量约 3000-4000 行。

---

## 四、关键技术难点与风险提示

### 4.1 技术难点

| 难点 | 描述 | 应对策略 |
|---|---|---|
| **意图识别准确率** | ML 分类器需要足够标注数据，运维场景标注数据获取困难 | 用规则 + LLM 生成训练数据冷启动，逐步积累真实标注 |
| **Plan-and-Solve 计划质量** | LLM 生成的计划可能不完整或含 Hallucination | 引入计划校验层（Schema + 语义校验），失败自动重试 |
| **ReAct 循环终止** | Agent 可能陷入无限循环 | 最大迭代次数 + Token 预算限制 + 重复检测 |
| **混合检索效果调优** | BM25 和向量检索权重分配、Rerank 模型选择 | 建立评测数据集，离线实验确定最优权重 |
| **MCP 工具管理** | 大量 MCP Server 的注册、发现、版本管理 | 构建 MCP Gateway 统一管理 |
| **A2A 通信可靠性** | Agent 间通信的延迟、失败重试、幂等性 | 采用消息队列异步解耦 + 持久化 + 重试 |
| **记忆系统一致性** | 多层记忆之间的同步和一致性问题 | 短期记忆为主工作区，摘要压缩异步，长期记忆最终一致 |
| **Prompt 注入防护** | 攻击手段不断演进 | 多层防御：输入清洗 + 指令边界隔离 + 检测模型 + 输出审计 |

### 4.2 风险提示

| 风险 | 影响 | 缓解措施 |
|---|---|---|
| **LLM API 不稳定** | 高 | 接入多个 LLM 提供商，实现自动故障切换 |
| **LLM 成本失控** | 高 | Token 预算管理 + 缓存策略 + 分级模型（简单任务用小模型） |
| **Spring AI 版本升级** | 中 | 封装接口，避免直接依赖内部实现 |
| **MCP 协议变更** | 中 | 关注协议版本兼容性 |
| **运维数据安全** | 高 | 四级安全控制 + 敏感数据脱敏 + 全程审计 |
| **团队学习曲线** | 中 | 从简单场景开始逐步深入，Spring AI 文档完善 |

---

## 五、推荐的项目目录结构

```
smart-ops-agent/
├── pom.xml                                    # 根 POM，统一依赖管理
├── docker-compose.yml                         # 本地开发环境依赖
│
├── smart-ops-common/                          # 公共模块
│   └── src/main/java/com/smartops/common/
│       ├── constant/                          # 常量与意图类型
│       ├── enums/                             # AgentMode, SecurityLevel, TaskStatus
│       ├── exception/                         # AgentException, SecurityViolationException
│       ├── model/                             # AgentCard, ExecutionPlan, TaskContext, IntentResult
│       └── util/                              # JsonUtils, PromptTemplate
│
├── smart-ops-domain/                          # 领域模块
│   └── src/main/java/com/smartops/domain/
│       ├── alert/                             # 告警领域（实体、仓库、服务）
│       ├── metric/                            # 指标领域
│       ├── log/                               # 日志领域
│       └── task/                              # 任务领域
│
├── smart-ops-infrastructure/                  # 基础设施模块
│   └── src/main/java/com/smartops/infra/
│       ├── memory/        # 记忆系统（ShortTerm, MediumTerm, LongTerm, Working）
│       ├── rag/           # RAG（HybridRetriever, BM25, Vector, RRF, Rerank, ETL）
│       ├── mcp/           # MCP（Client/Server 管理, ToolRegistry）
│       ├── a2a/           # A2A（AgentCardRegistry, MessageRouter, TaskManager）
│       ├── security/      # 安全（L0-L3 Filter, InjectionDetector）
│       ├── concurrency/   # 并发控制（RateLimiter, ConcurrencyController）
│       └── config/        # 基础设施配置（Redis, ES, MCP）
│
├── smart-ops-agent-core/                      # Agent 核心模块
│   └── src/main/java/com/smartops/agent/
│       ├── intent/        # 意图识别（Pipeline, L1-L4, ConflictResolver）
│       ├── router/        # 路由（AgentRouter, TaskAnalyzer, ReAct/PlanAndSolve Strategy）
│       ├── orchestrator/  # 编排（SupervisorAgent, WorkerRegistry, TaskDispatcher）
│       ├── worker/        # 子 Agent（Monitor, Analyze, Execute, Knowledge）
│       ├── plan/          # Plan-and-Solve（PlanGenerator, Validator, StepExecutor, Replanner）
│       └── react/         # ReAct（ReActLoop, ToolCallExecutor）
│
├── smart-ops-api/                             # API 层（Web 入口）
│   └── src/main/java/com/smartops/api/
│       ├── controller/    # AgentController, TaskController, AdminController, HealthController
│       ├── sse/           # SseController, SseEmitterManager, HeartbeatDetector
│       ├── dto/           # ChatRequest/Response, TaskStatusResponse
│       └── config/        # WebConfig, SseConfig
│
├── smart-ops-bootstrap/                       # 启动模块
│   └── src/main/java/com/smartops/
│       └── SmartOpsApplication.java
│   └── src/main/resources/
│       ├── application.yml / application-dev.yml / application-prod.yml
│       └── prompts/       # Prompt 模板（react-system.txt, plan-generator.txt 等）
│
└── smart-ops-web/                             # 前端（Vue3 可选）
    └── src/views/         # ChatView, DashboardView, AdminView
```

---

## 六、假设与决策

1. **LLM 选择**：优先使用国内可用的模型（通义千问/DeepSeek），预留 OpenAI 兼容接口做备选
2. **向量数据库**：首选 Elasticsearch 8.x（运维团队通常已有 ES 经验），备选 PGVector
3. **A2A 通信**（2026 修订）：跨 JVM 通信采用官方 a2a-java SDK + spring-ai-a2a，不再自研 gRPC；当前阶段保持 JVM 内通信
4. **ML 分类器**（2026 修订）：已移除 FastText 规划（无标注数据），意图识别定型为 L1/L2/L4 三层（ADR-011、ADR-013）
5. **前端**：可选 Vue3 + Element Plus，最低要求为 REST API + SSE 即可用
6. **部署**：Docker Compose 本地开发，Kubernetes 生产部署
7. **框架版本**（2026 新增）：Spring Boot 4.x + Spring AI 2.0.0（ADR-009），替代原 Spring Boot 3.x + Spring AI 1.0.0

---

## 七、验证步骤

1. **阶段一验证**：通过 curl 或 Postman 发自然语言查询 Prometheus 指标，确认返回正确结果
2. **阶段二验证**：构造不同复杂度任务，确认路由正确选择 ReAct 或 Plan-and-Solve
3. **阶段三验证**：多 Agent 协作完成复杂运维任务（如"分析告警 + 定位根因 + 执行修复"）
4. **阶段四验证**：知识库问答准确率评估，记忆系统跨会话一致性验证
5. **阶段五验证**：安全渗透测试（Prompt 注入、高风险操作拦截），压力测试（并发 1000+ 请求）