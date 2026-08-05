# Agent 协作开发指引（agent.md）

本文件是所有 AI Agent 参与 `smart-ops-agent`（智能运维 Agent 平台）项目开发时的统一行为准则。任何 Agent 在动手前必须完整阅读本文件，并在后续每次变更后同步更新项目知识库，确保后来者能够快速接续工作。

项目根目录：`d:\log-watcher`
规划文档：`智能运维Agent项目复现规划.md`（权威需求来源，本文件与其冲突时以规划文档为准）

---

## 一、项目定位

本项目复现一个基于 Spring Boot 4.x + Spring AI 2.0.0 构建的 Multi-Agent 智能运维平台（2026 年从 Boot 3.x + Spring AI 1.0.0 迁移，见 ADR-009）。核心能力包括：自然语言查询运维指标、自动化故障分析定位、智能告警处理、运维知识库问答。

平台将传统 Spring 生态的工程化能力与 AI Agent 技术深度融合，涵盖意图识别、智能路由、Multi-Agent 编排、多层记忆、混合检索 RAG、MCP 工具集成、安全控制、A2A 通信等完整能力。最终交付物为生产可用的智能运维平台，代码量预计 17000-24000 行，分 5 个阶段递进交付。

---

## 二、技术栈与版本约束

### 2.1 核心技术栈

| 类别 | 技术 | 版本 | 说明 |
|---|---|---|---|
| 语言 | Java | 17+ | Spring Boot 4.x 最低要求 |
| 框架 | Spring Boot | 4.0.x | 多模块 Maven 项目（ADR-009） |
| AI 框架 | Spring AI | 2.0.0 (GA) | 2026 年发布，需 Boot 4.x（ADR-009） |
| 构建工具 | Maven | 3.9+ | 统一 BOM 管理依赖 |
| 数据库 | MySQL | 8.x | 业务数据 |
| 缓存 | Redis | 7.x | 会话/缓存/长期记忆 |
| 搜索 | Elasticsearch | 8.x | BM25 + dense_vector 两级检索（Rerank 可选，ADR-013） |
| 向量存储 | PGVector | - | 备选方案，首选 ES |
| 通信 | a2a-java SDK | - | A2A Agent 间通信（跨 JVM 时启用，ADR-009；当前 JVM 内通信） |
| 容器化 | Docker Compose | - | 本地开发环境 |
| 编排 | Kubernetes | - | 生产部署 |

### 2.2 LLM 与模型选择

- **主 LLM**：通义千问或 DeepSeek（国内可用、成本低）
- **备选**：预留 OpenAI 兼容接口做故障切换
- **Embedding**：与所选 LLM 提供商配套
- **Rerank**：可选增强（bge-reranker-v2-m3），仅当两级检索评测不达标时引入（ADR-013）
- ~~**ML 分类器**~~：已移除（原规划 FastText 冷启动，无标注数据，见 ADR-011/ADR-013）

### 2.3 版本升级红线

- 核心框架版本以 ADR-009 为准（Boot 4.x + Spring AI 2.0.0），禁止直接依赖 Spring AI 内部实现，必须通过接口封装隔离
- MCP 协议版本变更需关注兼容性，新增 MCP Server 时核对协议版本（2.0 使用 Streamable HTTP 传输）
- 升级任何核心依赖前，先在 `smart-ops-common` 中验证接口兼容性

---

## 三、项目架构与模块划分

### 3.1 分层架构

项目采用四层架构，自上而下依次为：

1. **Presentation Layer**（表现层）：REST API、SSE 推送、Web Admin Console
2. **Application Layer**（应用层）：意图识别、Agent 路由、任务调度、会话管理、四个子 Agent（Monitor/Analyze/Execute/Knowledge）
3. **Infrastructure Layer**（基础设施层）：记忆系统、RAG 引擎、MCP 网关、A2A 通信、安全控制、限流、可观测性
4. **Data Layer**（数据层）：MySQL、Redis、Elasticsearch、PGVector

### 3.2 Maven 模块划分

| 模块 | 职责 | 关键组件 |
|---|---|---|
| `smart-ops-common` | 公共常量、枚举、异常、模型、工具 | `AgentMode`、`SecurityLevel`、`TaskStatus`、`AgentCard`、`ExecutionPlan`、`TaskContext`、`IntentResult` |
| `smart-ops-domain` | 领域模型（告警、指标、日志、任务） | 实体、仓库、领域服务 |
| `smart-ops-infrastructure` | 基础设施实现 | `memory/`、`rag/`、`mcp/`、`a2a/`、`security/`、`concurrency/`、`config/` |
| `smart-ops-agent-core` | Agent 核心逻辑 | `intent/`、`router/`、`orchestrator/`、`worker/`、`plan/`、`react/` |
| `smart-ops-api` | Web 入口 | `controller/`、`sse/`、`dto/`、`config/` |
| `smart-ops-bootstrap` | 启动模块 | `SmartOpsApplication.java`、`application*.yml`、`prompts/` |
| `smart-ops-web` | 前端（可选） | Vue3 + Element Plus |

详细的目录结构见 `智能运维Agent项目复现规划.md` 第五章。新增模块或目录时，先在该规划文档中补充说明，再落地代码。

### 3.3 核心创新点

- **三层意图识别**：L1 正则 → L2 关键词 → L4 LLM 兜底，配合冲突解决器归一化加权平均（L3 伪 ML 分类器已移除，ADR-011）
- **动态路由**：根据任务特征（步骤数、依赖关系、实时性、历史成功率）在 ReAct 与 Plan-and-Solve 之间动态选择
- **Multi-Agent 编排**：Supervisor-Worker 模式，Supervisor 负责任务分解与结果聚合，Worker 为各专业子 Agent
- **多层记忆**：短期（滑动窗口 20 条，会话级隔离 + LRU 上限）+ 中期（Redis 持久化，TTL 读时刷新）+ 工作记忆（执行器 TaskScratchpad，两级 LRU）；长期记忆延期（ADR-014）
- **两级检索 RAG**：BM25 + 向量检索 + RRF 融合（Rerank 可选，ADR-013）
- **安全控制**：高危操作人工确认门已前置落地（ADR-010）；阶段五补齐 L0 输入过滤 → L1 权限校验 → L2 操作审计，叠加 Prompt 注入防护

---

## 四、开发流程

采用"人类决策 → Feature 规划 → Spec 设计 → 任务分解 → TDD 实现 → 测试门禁 → 知识库同步 → 提交"的特性开发生命周期。任何特性开发必须按此顺序推进，不得跳过任何环节。

### 4.1 特性开发步骤

1. **人类决策**：由人类确定要开发的特性及优先级，明确验收标准
2. **Feature 规划**：Agent 阅读规划文档，将特性映射到对应阶段和模块，输出实现思路
3. **Spec 设计**：对特性做接口设计、数据结构设计、异常路径设计，形成简要 Spec
4. **任务分解**：将 Spec 拆分为可独立验证的子任务，每个子任务对应一次提交粒度
5. **TDD 实现**：先写测试（红），再写实现（绿），最后重构（重构）。测试不通过不得进入下一步
6. **测试门禁**：运行全量测试，覆盖率达标后方可继续
7. **知识库同步**：更新本文件与规划文档中受影响的部分，记录关键决策与踩坑
8. **提交**：通过门禁后提交 Git，提交信息包含具体内容

### 4.2 阶段交付节奏

严格按规划文档第三章的 5 阶段递进，不得越阶段开发：

- **阶段一**：核心骨架 + 单 Agent MVP（Spring AI + MCP 基础链路，约 2000-3000 行）
- **阶段二**：意图识别 + 智能路由（L1/L2/L4 三层识别 + ReAct/Plan-and-Solve 动态路由，约 4000-6000 行）
- **阶段三**：Multi-Agent 架构 + A2A 通信（Supervisor-Worker，跨 JVM 时用官方 a2a-java SDK，约 5000-7000 行）
- **阶段四**：记忆系统 + 两级检索 RAG（多层记忆 + BM25/向量 + RRF，Rerank 可选，约 3000-4000 行）
- **阶段五**：安全控制 + 生产化（L0-L2 安全 + 限流 + 可观测性，约 3000-4000 行）

每阶段完成后的验证标准见规划文档第七章。阶段未完成不得启动下一阶段，特殊情况需人类明确批准。

---

## 五、代码规范

### 5.1 命名规范

- **包名**：全小写，`com.smartops.{模块}.{子包}`，如 `com.smartops.agent.intent`
- **类名**：大驼峰，接口名不加 `I` 前缀，实现类用 `Impl` 后缀（如 `MemoryManagerImpl`）
- **方法名**：小驼峰，动词开头，查询类用 `find`/`get`/`query`，判断类用 `is`/`has`/`can`
- **常量**：全大写下划线，如 `MAX_REACT_ITERATIONS`
- **枚举**：大驼峰类名 + 全大写常量，如 `AgentMode.REACT`、`TaskStatus.RUNNING`
- **配置项**：`smartops.{模块}.{项}`，如 `smartops.memory.short-term.window-size`

### 5.2 注释规范

注释要求完善，这是硬性约束，不是可选项。

- **类注释**：每个类必须有 Javadoc，说明职责、关键依赖、线程安全性
- **公共方法**：必须注释 `@param`、`@return`、`@throws`，并在方法上方说明该方法做什么、为什么这样做
- **复杂逻辑**：意图识别冲突解决、RRF 融合算法、Plan 校验等复杂逻辑必须有行内注释解释思路
- **Prompt 模板**：`resources/prompts/` 下的每个 `.txt` 文件顶部注释说明用途、输入变量、期望输出格式
- **配置项**：`application.yml` 中每个自定义配置必须有注释说明含义与默认值
- **避免废话注释**：不要写 `// 获取用户` 这种重述方法名的注释，要写为什么和注意事项

### 5.3 代码组织

- 单一职责：一个类只做一件事，一个方法不超过 50 行
- 依赖方向：`bootstrap` → `api` → `agent-core` → `domain` → `infrastructure` → `common`，禁止反向依赖
- 接口优先：基础设施层对外暴露接口，实现在 `impl` 子包，便于替换（如 VectorStore 切换 ES/PGVector）
- 异常分层：`smart-ops-common` 定义基础异常，各模块派生领域异常，禁止直接抛 `RuntimeException`
- 配置外置：环境相关配置全部走 `application-{profile}.yml`，代码中不硬编码环境信息

---

## 六、测试要求

测试覆盖是提交前的硬性门禁。不写测试的代码视为未完成。

### 6.1 测试分层

| 层级 | 测试类型 | 工具 | 覆盖范围 |
|---|---|---|---|
| 单元测试 | 纯逻辑测试 | JUnit 5 + Mockito | 工具类、算法、领域逻辑、纯函数 |
| 集成测试 | 模块内集成 | Spring Boot Test + Testcontainers | Repository、Service、Adapter |
| 接口测试 | API 层 | MockMvc / WebTestClient | Controller、SSE、异常处理 |
| 端到端测试 | 关键链路 | Spring Boot Test 全量启动 | 阶段验证中的核心场景 |

### 6.2 覆盖率要求

- **行覆盖率**：不低于 95%
- **分支覆盖率**：不低于 90%
- **核心模块**（`intent`、`router`、`orchestrator`、`security`、`memory`）：行覆盖率不低于 95%
- 外部依赖（LLM、Prometheus、MySQL、Redis、ES）一律用 Mock 或 Testcontainers，不在单测中打真实服务

### 6.3 测试编写规范

- 测试类与被测类同包，命名 `{ClassName}Test`
- 测试方法命名 `should_{期望行为}_when_{前置条件}`，如 `should_returnReactMode_when_taskHasSingleStep`
- 每个测试方法只验证一个行为，Arrange-Act-Assert 三段式
- 测试数据用 Builder 或工厂方法构造，禁止在多个测试间共享可变状态
- LLM 调用必须 Mock，避免测试不稳定和成本消耗

### 6.4 运行测试

提交前必须运行全量测试并通过：

```bash
mvn clean test
mvn verify -Pintegration  # 包含集成测试
```

测试失败时优先修复测试，而不是调整测试期望值来迁就错误实现。

---

## 七、版本控制规范

每次开发完成（一个子任务或一个特性）后必须提交 Git，防止工作遗漏。提交信息必须包含具体内容，禁止使用 `update`、`fix bug` 这类无信息量的描述。

### 7.1 提交信息格式

```
[{阶段}-{模块}] {简要描述}

{详细说明：做了什么、为什么、影响范围}

{关联的子任务编号或 Spec 引用}
```

示例：

```
[阶段一-mcp] 实现 Prometheus MCP Client 基础连接

通过 spring-ai-starter-mcp-client 连接本地 Prometheus MCP Server，
实现 queryRange 工具的调用封装。包含连接重试与超时处理。

关联任务：阶段一-任务3
```

### 7.2 分支策略

- `main`：稳定分支，每阶段验证通过后合并
- `develop`：开发主干，子任务完成后合并
- `feature/{阶段}-{特性名}`：特性分支，如 `feature/phase1-mcp-client`
- `fix/{问题描述}`：修复分支

### 7.3 提交粒度

- 一次提交对应一个可独立验证的子任务
- 测试与实现一起提交，不得拆分为"先提交实现再提交测试"
- 重构单独提交，不与功能变更混合

---

## 八、知识库管理

每次变更后必须同步更新项目知识库，保证后续 Agent 能快速接续。这是用户的核心偏好，不可省略。

### 8.1 需要同步更新的文件

| 文件 | 更新时机 | 更新内容 |
|---|---|---|
| `agent.md`（本文件） | 架构、规范、流程变更时 | 对应章节 |
| `智能运维Agent项目复现规划.md` | 模块、目录、阶段任务调整时 | 对应章节 |
| `docs/decisions/`（待建） | 做出关键技术决策时 | 新增 ADR（架构决策记录） |
| `docs/solutions/`（待建） | 解决非平凡问题后 | 问题现象、根因、解决方案 |

### 8.2 ADR 记录格式

每个关键决策记录为一个独立文件，命名 `ADR-{编号}-{简要标题}.md`，内容包含：

- **背景**：为什么需要做这个决策
- **决策**：最终选择了什么
- **备选方案**：考虑过哪些方案，为什么放弃
- **影响**：这个决策对后续开发的约束

### 8.3 问题解决方案记录

遇到并解决非平凡问题（如 Spring AI 版本兼容、MCP 连接异常、RAG 检索效果差）后，在 `docs/solutions/` 下记录，避免相同问题重复排查。记录内容：问题现象、复现步骤、根因分析、解决方案、预防措施。

---

## 九、关键决策与约束

以下决策来自规划文档第六章，开发过程中必须遵守，变更需人类明确批准。

1. **LLM 选择**：优先国内可用模型（通义千问/DeepSeek），预留 OpenAI 兼容接口做备选
2. **向量数据库**：首选 Elasticsearch 8.x，备选 PGVector。理由是运维团队通常已有 ES 经验
3. **A2A 通信**（2026 修订）：跨 JVM 通信采用官方 a2a-java SDK + spring-ai-a2a，不再自研 gRPC；当前保持 JVM 内通信（ADR-009）
4. **ML 分类器**（2026 修订）：已移除 FastText 规划（无标注数据），意图识别定型为 L1/L2/L4 三层（ADR-011/ADR-013）
5. **前端**：可选 Vue3 + Element Plus，最低要求 REST API + SSE 即可用
6. **部署**：Docker Compose 本地开发，Kubernetes 生产部署

### 9.1 技术难点应对

开发中遇到以下难点时，优先采用规划文档第四章给出的应对策略：

- **意图识别准确率**：规则 + LLM 生成训练数据冷启动，逐步积累真实标注
- **Plan-and-Solve 计划质量**：引入计划校验层（Schema + 语义校验），失败自动重试
- **ReAct 循环终止**：最大迭代次数 + Token 预算限制 + 重复检测
- **混合检索效果调优**：建立评测数据集，离线实验确定最优权重
- **MCP 工具管理**：构建 MCP Gateway 统一管理注册、发现、版本
- **A2A 通信可靠性**：消息队列异步解耦 + 持久化 + 重试
- **记忆系统一致性**：短期记忆为主工作区，摘要压缩异步，长期记忆最终一致
- **Prompt 注入防护**：多层防御（输入清洗 + 指令边界隔离 + 检测模型 + 输出审计）

---

## 十、质量门禁

提交前必须通过以下全部检查，任何一项失败都不得提交：

- [ ] 全量单元测试通过（`mvn clean test`）
- [ ] 集成测试通过（`mvn verify -Pintegration`）
- [ ] 行覆盖率 ≥ 95%，核心模块 ≥ 95%
- [ ] 新增/修改的公共方法有 Javadoc
- [ ] 新增配置项有注释说明
- [ ] Prompt 模板文件有顶部注释
- [ ] 受影响的文档已同步更新（本文件或规划文档）
- [ ] 提交信息符合第七章格式
- [ ] 无硬编码的环境信息（IP、密码、密钥）
- [ ] 异常处理符合分层规范，无裸 `RuntimeException`

---

## 十一、Agent 工作清单

每次接手任务时，按以下顺序工作：

1. 完整阅读本文件与 `智能运维Agent项目复现规划.md`
2. 检查 `docs/decisions/` 和 `docs/solutions/` 是否有相关历史记录
3. 确认当前所处阶段与未完成任务
4. 按第四章开发流程推进：规划 → Spec → 分解 → TDD → 门禁 → 同步 → 提交
5. 遇到规划文档未覆盖的问题，先记录到 `docs/decisions/` 或 `docs/solutions/`，再寻求人类决策
6. 每次工作结束前，更新本文件第十二章的"开发进度"章节

---

## 十二、开发进度

> 本章节由 Agent 在每次工作结束时更新，记录当前进展。

### 2026-07 规划审查与修复期（已完成）

规划审查结论：五阶段路线方向合理，但安全控制排期过晚、FastText 无标注数据难落地、三层 RAG 过度工程、框架版本过时；代码存在多个 P0 级缺陷（会话记忆全局共享、PrometheusTools 编造数据、安全门死代码、ReAct 迭代上限失效、意图置信度体系失真、Worker 纯回显桩、Supervisor 假阳性成功）。已完成以下修复（每步 `mvn verify` 全绿后提交）：

| 步骤 | 内容 | 关联 ADR |
|---|---|---|
| S0 | 迁移 Spring Boot 4.0.7 + Spring AI 2.0.0（MCP 改 Streamable HTTP，A2A 决策改官方 SDK） | ADR-009 |
| S1 | 会话级记忆隔离：conversationId 全链路透传 + LRU 上限，修复跨用户上下文泄露 | — |
| S2 | PrometheusTools 接真实数据，删除随机编造逻辑，不可用时显式 unavailable | — |
| S3 | 最小安全门：高危操作人工确认 + 一次性确认令牌 | ADR-010 |
| S4 | ReAct 迭代上限经 ToolCallRoundGate 强制落地；PlanAndSolve 续接式重规划（上限 2 次） | — |
| S5 | 意图置信度体系重设计：L1 0.9/0.4 分层、L2 ≤0.79、删除 L3 伪 ML、L4 真实置信度、权重 1.0/0.7/0.9 | ADR-011 |
| S6 | TaskAnalyzer 路由修复：探索性收紧、EXECUTE_OPERATION 永不探索、实时性决胜、步骤计数去重 | — |
| S7 | 四个 Worker 接入真实 LLM + 角色专属外置提示词，回显桩全部移除 | — |
| S8 | Supervisor 加固：全失败语义 success=false、子任务超时（60s 可配）、同角色注册拒绝 | — |
| S9 | SSE 加固：[ERROR] 终止事件 + 15s 心跳 + 日志截断脱敏 | ADR-012 |
| S10 | 异常分层：LlmCallException 边界翻译，裸 catch(Exception) 全部收窄为 AgentException | — |
| S11 | 文档同步（本节 + 规划文档修订） | ADR-013 |

新增集成测试：IT-1 记忆隔离、IT-2 安全门令牌流（AgentControllerTest.ConfirmationFlow + SecurityGateFlowTest）、IT-3 路由模式选择（RouterModeSelectionTest）。

- **当前阶段**：阶段一（核心骨架 + 单 Agent MVP）— 已完成，待真实环境验证
- **阶段一进度**：
  - [x] 任务1：搭建 Spring Boot 3.x + Spring AI 1.0.0 基础项目（多模块 Maven）— 7 个模块全部编译通过
  - [x] 任务2：接入 LLM（DeepSeek 通过 OpenAI 兼容协议）— `ChatClientConfig` 配置完成
  - [x] 任务3：实现 MCP Client 端（连接 Prometheus MCP Server）— `application.yml` SSE 配置 + `McpClientConfig` 完成
  - [x] 任务4：实现基础 Tool Calling（`ChatClient` + `@Tool`）— `PrometheusTools` 含 queryMetric/queryMetricRange
  - [x] 任务5：实现短期记忆（`MessageWindowChatMemory`，滑动窗口 20 条）— `ChatMemoryConfig` 完成
  - [x] 任务6：实现 REST API + SSE 推送 — `AgentController`（同步）+ `SseController`（流式）+ `HealthController`
  - [x] 任务7：搭建 MySQL + Redis — `docker-compose.yml` 已就绪（Docker 未安装，需用户自行安装或本地部署）
- **阶段二进度**：已完成（意图识别 + 智能路由）
  - [x] 特性1：三层意图识别 Pipeline（L1 正则 → L2 关键词 → L4 LLM 兜底；L3 伪 ML 已移除，ADR-011）
  - [x] 特性2：冲突解决器（归一化加权平均，L1=1.0, L2=0.7, L4=0.9，ADR-011）
  - [x] 特性3：意图识别 Pipeline 编排（短路机制 + 异常降级 + 诊断信息）
  - [x] 特性4：TaskAnalyzer 任务复杂度分析（步骤计数 + 依赖检测 + 探索性判断 + 模式选择）
  - [x] 特性5：ReAct 执行器（Thought→Action→Observation 循环，MAX_ITERATIONS=10）
  - [x] 特性6：PlanGenerator + PlanAndSolveExecutor（先规划后执行 + 重规划 + 总结生成，MAX_REPLAN=3）
  - [x] 特性7：AgentRouter 动态路由引擎（意图识别→复杂度分析→模式选择→执行→异常兜底）
  - [x] 特性8：ChatService 封装 ChatClient fluent API（供上层测试，三种调用模式）
  - [x] 特性9：API 层集成 AgentRouter（AgentController 升级 + ChatResponse 增强执行元数据）
- **阶段三进度**：已完成（Multi-Agent 架构 + A2A 通信）
  - [x] 特性1：AgentCard + AgentCardRegistry（能力描述与注册发现，ConcurrentHashMap 线程安全）
  - [x] 特性2：A2A 消息协议（A2aRequest/A2aResponse/SubTask，不可变 record + 防御性拷贝）
  - [x] 特性3：4个专业 Worker Agent（Monitor/Analyze/Execute/Knowledge，AbstractWorkerAgent 模板方法模式）
  - [x] 特性4：TaskDispatcher 子任务路由分发（按角色查找 Worker + 构造 A2A 请求）
  - [x] 特性5：SupervisorAgent 任务分解与结果聚合（关键词规则分解 + 按优先级执行 + 结果聚合）
  - [x] 特性6：WorkerRegistrar 自动注册（Spring ApplicationReadyEvent 触发）
  - [x] 特性7：AgentRouter 集成 Supervisor（跨领域复杂任务自动路由至多 Agent 协作）
- **阶段四进度**：已完成（多层记忆 + 两级混合检索 RAG）
  - [x] P0：ADR-014 记忆分层 / ADR-015 Ollama bge-m3 Embedding / ADR-016 ES VectorStore + RRF 回退；docker-compose 加 ES 8.14.3 单节点
  - [x] P1：domain 检索模型 `KnowledgeChunk` + `KnowledgeRetriever` 端口（失败返回空表的降级契约）
  - [x] P2：本地 Ollama Embedding 配置（独立 `smartops.embedding.*` 命名空间，默认关闭，bge-m3 1024 维）
  - [x] P3：ES 两级检索——向量路（ElasticsearchVectorStore）+ BM25 文本路 + 客户端 RRF 融合（k=60，ES store 2.0.0 无原生 hybrid，ADR-016 回退策略）
  - [x] P4：Markdown 知识库 ETL（标题感知切块 + 幂等 stableId + 维度探针校验 + ApplicationRunner 门控）
  - [x] P5：KnowledgeAgent 接入 RAG（命中注入【知识库检索结果】上下文，空/异常降级保留未接入前缀）
  - [x] P6：中期记忆 Redis 持久化（JSON 三元组存储 + TTL 读时刷新 + TOOL 过滤 + 损坏降级，`smartops.memory.mid-term.enabled` 切换）
  - [x] P7：工作记忆 TaskScratchpad（按会话两级 LRU；ReAct/PlanAndSolve 执行中写入步骤、任务结束清理）
- **阶段五进度**：进行中
  - [x] P1：logwatch — 日志采集 → 关键字检出 → 六层分析（L0 抑制 → L1 正则定级 → L2 ML 定级救援 → L3 RAG → L4 LLM → L5 会诊）→ 告警落库/SSE（17 commits，已部署生产；ML 层见阶段十五 ADR-019）
  - [x] P2：可观测性 + L2 操作审计 — Micrometer 指标（LLM/工具/任务/安全）+ async_event 异步落库/查询/Prometheus 端点（8 commits，已部署生产）
  - [x] P3：多模型 Provider 抽象层 — LlmProviderRegistry 按能力选择、温度约束、OpenAiLlmProvider、ChatService 自动路由
  - [x] P4：L0 输入过滤器 — XSS/SQL/命令注入正则阻断、超长拒绝
  - [x] P5：Prompt 注入防护 — 所有模板追加防御句、PromptWrapper 边界包裹
  - [x] P6：LLM 调用滑动窗口限流 — SlidingWindowRateLimiter 分钟级上限保护
  - [x] P7：L4LLMRecognizer 审计盲区修复 — 改为走 ChatService 漏斗
  - [x] P8：工具调用 Semaphore 并发控制
  - [x] P9：Resilience4j 熔断保护（spring-boot4 2.4.0 已验证兼容）
  - [x] P10：AgentRouter 有界任务队列（TaskExecutorConfig + CallerRunsPolicy）
  - [x] P11：Kimi 工具兼容路由方案落地（prod yml 多 Provider：Moonshot+DeepSeek）
  - [x] P12：SSE 断线重连——SseTask 热流 + SseTaskRegistry 会话缓存
- **阶段五状态**：已全部完成（P1-P12）
- **阶段六进度**：进行中
- **阶段七进度**：已全部完成（P1-P8）
  - [x] P1：服务拓扑图（ECharts + JPA + V5 SQL）
  - [x] P2：ML异常检测引擎（统计基线EMA+RMS）
  - [x] P3：多渠道通知系统（Webhook/Slack/钉钉/邮件）
  - [x] P4：Runbook自动修复引擎
  - [x] P5：跨源事件关联 + 事件管理
  - [x] P6：事后复盘（LLM生成报告 → 知识库）
  - [x] P7：第三方集成适配器（Jira/GitHub Webhook）
  - [x] P8：预测分析 + 仪表盘升级（ECharts + 实时统计）
  - [x] P1：服务器配置管理 CRUD（ServerConfig + REST API + V3 SQL）
  - [x] P2：知识库领域模型 + 持久层（KnowledgeEntry + JPA + V4 SQL）
  - [x] P3：知识库 REST CRUD + 搜索 + logwatch 自动入库
  - [x] P4：Vue 3 前端项目骨架 + 全部页面（对话/告警/审计/知识库/服务器/仪表盘）
  - [ ] P5：前端部署（npm build → Spring Boot static）
  - [ ] P6：生产部署验证

### 阶段一验证状态

- [x] 全量编译通过：`mvn clean compile -DskipTests`（7 模块 SUCCESS）
- [x] 单元测试通过：`mvn test`（common 31 + infrastructure 11 + agent-core 20 + api 28 = 90 个测试全绿）
- [x] 覆盖率门禁通过：`mvn verify`（行覆盖率 ≥ 95%，分支覆盖率 ≥ 90%，全模块 "All coverage checks have been met"）
  - common 100% / infrastructure 100% / agent-core 97.6% / api 100%
- [ ] 真实 LLM 调用验证：需配置 `DEEPSEEK_API_KEY` 环境变量后启动应用，通过 `/api/agent/chat` 发自然语言查询
- [ ] MCP 真实连接验证：需启动 Prometheus MCP Server 后验证工具调用链路
- [ ] MySQL/Redis 连接验证：需启动 docker-compose 或本地数据库后验证持久化

### 阶段二验证状态

- [x] 全量编译通过：`mvn clean compile -DskipTests`（7 模块 SUCCESS）
- [x] 单元测试通过：`mvn verify`（common 94 + infrastructure 28 + agent-core 207 + api 35 = 364 个测试全绿）
- [x] 覆盖率门禁通过：`mvn verify`（行覆盖率 ≥ 95%，分支覆盖率 ≥ 90%，全模块 "All coverage checks have been met"）
- [ ] 端到端集成验证：需配置 `DEEPSEEK_API_KEY` 后验证 意图识别 → 路由决策 → ReAct/Plan-and-Solve 完整链路
- [ ] 路由决策准确性验证：需真实 LLM 调用验证四层识别与模式选择的准确性

### 阶段三验证状态

- [x] 全量编译通过：`mvn clean compile -DskipTests`（7 模块 SUCCESS）
- [x] 单元测试通过：`mvn verify`（common 223 + infrastructure 28 + agent-core 343 + api 35 = 629 个测试全绿）
- [x] 覆盖率门禁通过：`mvn verify`（全模块 "All coverage checks have been met"，新增类覆盖率 100%）
- [ ] 端到端集成验证：需启动应用后验证 Supervisor → Worker A2A 通信完整链路
- [ ] 多 Agent 协作准确性验证：需真实 LLM 调用验证任务分解与结果聚合质量

### 阶段四验证状态

- [x] 全量编译通过：`mvn clean compile -DskipTests`（7 模块 SUCCESS）
- [x] 单元测试通过：`mvn verify`（全模块全绿，新增检索/ETL/记忆类 Mockito 覆盖降级与开关分支）
- [x] 覆盖率门禁通过：`mvn verify`（全模块 "All coverage checks have been met"）
- [x] 薄 IT 门控：`EsKnowledgeRetrieverIT` 等 `@EnabledIfEnvironmentVariable(SMARTOPS_IT=true)`，默认不跑
- [ ] 端到端冒烟：需 Docker Desktop + Ollama——`docker compose up -d` → `ollama pull bge-m3` → 开 `smartops.embedding/elasticsearch/knowledge.etl.enabled` 验证检索增强；开 `mid-term.enabled` 重启验证会话存活

### 已记录的决策

- [ADR-001：Spring AI 1.0.0 GA 的 starter 命名采用 model- 前缀](docs/decisions/ADR-001-spring-ai-starter-naming.md)
- [ADR-002：DeepSeek 通过 OpenAI 兼容协议接入](docs/decisions/ADR-002-deepseek-via-openai-protocol.md)
- ADR-003：意图识别采用短路机制（2026 修订：L1 具体规则置信度 0.9、短路阈值 0.85；宽泛兜底正则降至 0.4 不短路，ADR-011）
- ADR-004：TaskAnalyzer 模式选择优先级：探索性（EXECUTE_OPERATION 豁免）/ROOT_CAUSE → 实时性 → 步骤数 → 默认 ReAct
- ADR-005：ChatService 封装 ChatClient fluent API 以降低上层测试复杂度
- ADR-006：Multi-Agent 采用 Supervisor-Worker 模式，同 JVM 内通信（跨 JVM 时用官方 a2a-java SDK，ADR-009）
- ADR-007：Worker Agent 使用模板方法模式，AbstractWorkerAgent 封装公共逻辑（注册/校验/异常捕获）
- ADR-008：AgentRouter 跨领域任务检测（2+ 域关键词 + 3+ 步骤）自动路由至 SupervisorAgent
- [ADR-009：迁移 Spring Boot 4.x + Spring AI 2.0.0，A2A 改官方 SDK](docs/decisions/ADR-009-boot4-spring-ai-2-migration.md)
- [ADR-010：安全控制提前——最小安全门与一次性确认令牌](docs/decisions/ADR-010-security-gate-early.md)
- [ADR-011：意图置信度体系重设计（含 L3 伪 ML 移除）](docs/decisions/ADR-011-intent-confidence-redesign.md)
- [ADR-012：SSE 流式路径直连 ChatClient，不经路由管线](docs/decisions/ADR-012-sse-bypasses-routing-pipeline.md)
- [ADR-013：阶段四简化——移除 FastText，RAG 降为两级检索](docs/decisions/ADR-013-phase4-simplification.md)
- [ADR-014：记忆分层——短期窗口 / 中期 Redis / 工作记忆，长期延期](docs/decisions/ADR-014-memory-layering.md)
- [ADR-015：Embedding 选型——本地 Ollama bge-m3，独立配置命名空间](docs/decisions/ADR-015-embedding-ollama-bge-m3.md)
- [ADR-016：RAG 选型——ElasticsearchVectorStore + 客户端 RRF 回退 + ETL 门控](docs/decisions/ADR-016-rag-es-vectorstore.md)
- [ADR-017：logwatch 日志采集分析告警——只读采集、手工迁移、分层降级](docs/decisions/ADR-017-logwatch-pipeline.md)
- [ADR-018：可观测性与异步审计](docs/decisions/ADR-018-observability-audit.md)
- [ADR-019：ML 日志定级层——正则漏判救援、只升不降、准确率门禁](docs/decisions/ADR-019-ml-level-classifier.md)

### 已解决的问题

- [阶段一环境配置问题（JDK 版本与 Maven 配置）](docs/solutions/phase1-environment-setup.md)
- 阶段二：L1RegexRecognizer Pattern.compile 字符串拼接错误（需在 compile 内拼接）
- 阶段二：ConflictResolver 加权投票置信度计算（加权和/总权重）
- 阶段二：IntentPipeline 短路机制测试（L1 具体规则 0.9 ≥ 0.85 短路）
- 阶段二：TaskAnalyzer selectMode 优先级调整（探索性检查前置于步骤数）
- 阶段二：ChatService chatWithTools 空值保护（toolBeans 为 null 时不调用 tools()）
- 修复期：ConfirmationContext（ThreadLocal）跨 CompletableFuture 异步边界丢失——调用线程捕获 isConfirmed 后在异步 lambda 内 mark/clear
- 修复期：Mockito 5 varargs 打平——argThat((Object[]) -> ...) 收到的是元素本身（ClassCastException），单元素校验用 eq(element)
- 修复期：StepVerifier 虚拟时间与 publish().autoConnect(2) + interval 组合不兼容（心跳事件不触发）——改为测试构造器注入短心跳间隔用真实时钟验证

### 阶段一关键文件清单

| 模块 | 关键文件 | 说明 |
|---|---|---|
| 根 | `pom.xml` | 多模块聚合 POM，Spring Boot 4.0.7 parent + Spring AI 2.0.0 BOM 管理（ADR-009） |
| 根 | `docker-compose.yml` | MySQL 8 + Redis 7 本地开发环境 |
| common | `enums/AgentMode.java` | ReAct / Plan-and-Solve 模式枚举 |
| common | `enums/TaskStatus.java` | 任务状态枚举 |
| common | `enums/SecurityLevel.java` | 四级安全等级枚举 |
| common | `enums/IntentType.java` | 意图类型枚举（8 种运维意图） |
| common | `exception/AgentException.java` | 基础异常类 |
| common | `exception/SecurityViolationException.java` | 安全违规异常 |
| common | `exception/LlmCallException.java` | LLM 调用失败异常（LLM_CALL_FAILED，S10 异常分层） |
| infrastructure | `config/ChatClientConfig.java` | ChatClient Bean 配置（系统提示词 + 记忆 Advisor） |
| infrastructure | `config/ChatMemoryConfig.java` | 短期记忆配置（MessageWindowChatMemory + 会话数 LRU 上限） |
| infrastructure | `mcp/McpClientConfig.java` | MCP Client 条件化配置（smartops.mcp.enabled 开关） |
| agent-core | `tools/PrometheusTools.java` | Prometheus 指标查询工具（@Tool 注解，接真实数据，S2） |
| agent-core | `security/SecurityGate.java` | 最小安全门：高危操作分级 + 确认校验（ADR-010） |
| agent-core | `security/ConfirmationContext.java` | 人工确认标记（ThreadLocal，跨异步边界透传） |
| agent-core | `security/ConfirmationTokenStore.java` | 一次性确认令牌存储（绑定会话 + 消息，TTL 10 分钟） |
| api | `controller/AgentController.java` | 同步对话接口 `/api/agent/chat`（集成 AgentRouter + 确认令牌流） |
| api | `controller/SseController.java` | SSE 流式对话接口（[ERROR] 终止事件 + 15s 心跳 + 日志脱敏，ADR-012） |
| api | `controller/HealthController.java` | 健康检查接口 `/api/health` |
| api | `dto/ChatRequest.java` | 对话请求 DTO |
| api | `dto/ChatResponse.java` | 对话响应 DTO（含执行元数据：mode/iterations/success/errorMessage） |
| bootstrap | `SmartOpsApplication.java` | 应用启动类 |
| bootstrap | `application.yml` | 公共配置（Spring AI + MCP + 记忆） |
| bootstrap | `application-dev.yml` | 开发环境配置（MySQL + Redis） |
| bootstrap | `application-prod.yml` | 生产环境配置（环境变量注入） |
| bootstrap | `prompts/react-system.txt` | ReAct 模式系统提示词 |

### 阶段二关键文件清单

| 模块 | 关键文件 | 说明 |
|---|---|---|
| common | `model/IntentResult.java` | 意图识别结果（意图类型 + 置信度 + 来源 + 实体） |
| common | `model/TaskComplexity.java` | 任务复杂度分析结果（步骤数 + 依赖 + 实时性 + 探索性 + 建议模式） |
| common | `model/AgentExecutionResult.java` | Agent 执行结果（答案 + 模式 + 迭代数 + 步骤 + 成功状态 + 错误信息） |
| common | `model/ExecutionPlan.java` | 执行计划（目标 + 步骤列表 + 校验状态），含 PlanStep 内部记录 |
| agent-core | `intent/IntentRecognizer.java` | 意图识别器接口 |
| agent-core | `intent/L1RegexRecognizer.java` | L1 正则识别器（具体规则 0.9 / 宽泛兜底 0.4 两级置信度） |
| agent-core | `intent/L2KeywordRecognizer.java` | L2 关键词识别器（置信度上限 0.79，ADR-011） |
| agent-core | `intent/L4LLMRecognizer.java` | L4 LLM 兜底识别器（JSON 真实置信度 + 外置提示词） |
| agent-core | `intent/ConflictResolver.java` | 冲突解决器（归一化加权平均 L1=1.0/L2=0.7/L4=0.9） |
| agent-core | `intent/IntentPipeline.java` | 意图识别 Pipeline（三层短路 0.85 + 异常降级 + 诊断） |
| agent-core | `router/TaskAnalyzer.java` | 任务复杂度分析器（步骤计数 + 依赖检测 + 模式选择） |
| agent-core | `router/AgentRouter.java` | 路由决策引擎（意图识别→复杂度分析→模式选择→执行） |
| agent-core | `react/ReActExecutor.java` | ReAct 执行器（Thought→Action→Observation 循环） |
| agent-core | `plan/PlanGenerator.java` | 计划生成器（LLM 生成 + 正则解析 + 默认计划兜底） |
| agent-core | `plan/PlanAndSolveExecutor.java` | Plan-and-Solve 执行器（规划→校验→执行→重规划→总结） |
| infrastructure | `chat/ChatService.java` | LLM 对话服务封装（会话/无状态双路径 + LlmCallException 边界翻译） |
| infrastructure | `advisor/ToolCallRoundGate.java` | 工具调用轮次门（ThreadLocal，ReAct 迭代上限强制落地） |

### 阶段三关键文件清单

| 模块 | 关键文件 | 说明 |
|---|---|---|
| common | `enums/AgentRole.java` | Agent 角色枚举（SUPERVISOR/MONITOR/ANALYZE/EXECUTE/KNOWLEDGE） |
| common | `model/AgentCard.java` | Agent 能力卡片（角色 + 专长 + 支持意图 + 并发能力） |
| common | `model/SubTask.java` | 子任务（目标角色 + 执行指令 + 优先级 + 状态 + 结果） |
| common | `model/A2aRequest.java` | A2A 请求消息（请求 ID + 子任务 ID + 源/目标角色 + 指令） |
| common | `model/A2aResponse.java` | A2A 响应消息（状态 + 结果 + 错误信息 + 工厂方法） |
| agent-core | `a2a/AgentCardRegistry.java` | Agent Card 注册中心（注册/注销/按角色/按意图查询） |
| agent-core | `worker/WorkerAgent.java` | Worker Agent 接口（getCard + handle） |
| agent-core | `worker/AbstractWorkerAgent.java` | Worker 抽象基类（模板方法：注册 + 校验 + 异常捕获） |
| agent-core | `worker/MonitorAgent.java` | 监控 Agent（LLM + PrometheusTools，角色专属提示词） |
| agent-core | `worker/AnalyzeAgent.java` | 分析 Agent（LLM 上下文分析，角色专属提示词） |
| agent-core | `worker/ExecuteAgent.java` | 执行 Agent（安全门前置 + LLM 生成操作方案，执行为模拟） |
| agent-core | `worker/KnowledgeAgent.java` | 知识 Agent（RAG 检索增强 + 空/异常降级未接入前缀，阶段四 P5） |
| agent-core | `orchestrator/TaskDispatcher.java` | 任务分发器（按角色查找 Worker + 同角色重复注册拒绝） |
| agent-core | `orchestrator/SupervisorAgent.java` | Supervisor Agent（任务分解 + 优先级排序 + 子任务超时 + 全失败语义） |
| agent-core | `orchestrator/WorkerRegistrar.java` | Worker 自动注册器（ApplicationReadyEvent 触发） |
| agent-core | `router/AgentRouter.java` | 路由决策引擎（集成 Supervisor 多 Agent 协作） |

### 阶段四关键文件清单

| 模块 | 关键文件 | 说明 |
|---|---|---|
| 根 | `docker-compose.yml` | 新增 ES 8.14.3 单节点服务（Ollama 为宿主机本地服务，不入 compose） |
| domain | `knowledge/KnowledgeChunk.java` | 检索结果记录（id/content/source/title/score） |
| domain | `knowledge/KnowledgeRetriever.java` | 检索端口（契约：失败返回空表不抛异常） |
| infrastructure | `config/EmbeddingConfig.java` | Ollama Embedding 配置（smartops.embedding.*，默认关闭） |
| infrastructure | `config/VectorStoreConfig.java` | Rest5Client + ElasticsearchVectorStore + 检索器装配（smartops.elasticsearch.*） |
| infrastructure | `knowledge/impl/EsKnowledgeRetriever.java` | 两级检索：向量路 + BM25 路 + 客户端 RRF 融合（k=60） |
| infrastructure | `knowledge/etl/MarkdownChunker.java` | Markdown 标题感知切块（chunk-size 兜底按行边界再切） |
| infrastructure | `knowledge/etl/KnowledgeIndexer.java` | 批量写入向量库（幂等 stableId + 维度探针校验） |
| infrastructure | `knowledge/etl/KnowledgeEtlRunner.java` | ETL 启动 Runner（smartops.knowledge.etl.enabled 门控） |
| infrastructure | `memory/WorkingMemory.java` | 工作记忆端口（put/get/clear，按 conversationId 隔离） |
| infrastructure | `memory/impl/InMemoryWorkingMemory.java` | 工作记忆进程内实现（会话数 + 单会话条目两级 LRU） |
| infrastructure | `memory/impl/RedisChatMemoryRepository.java` | 中期记忆 Redis 仓库（JSON 三元组 + TTL 读时刷新 + TOOL 过滤） |
| infrastructure | `config/ChatMemoryConfig.java` | 记忆仓库条件装配（mid-term.enabled 切换 内存/Redis） |
| agent-core | `worker/KnowledgeAgent.java` | 接入 KnowledgeRetriever：命中注入检索上下文，降级保留前缀 |
| agent-core | `react/ReActExecutor.java` | 执行中写 react.steps 工作记忆，任务结束清理 |
| agent-core | `plan/PlanAndSolveExecutor.java` | 每批步骤后写 plan.steps 工作记忆，try/finally 保证清理 |
| bootstrap | `application.yml` | 新增 smartops.embedding/elasticsearch/knowledge.etl/memory.mid-term/memory.working 配置键 |
