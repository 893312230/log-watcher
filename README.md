# SmartOps-Agent 智能运维平台

基于 **Spring Boot 4 + Spring AI 2.0 + Vue 3** 的多 Agent 智能运维（AIOps）平台：自然语言驱动日志诊断、告警分析、Runbook 自动化处置与运维知识库问答，全链路已上生产（阿里云最小化部署，Docker Compose 全栈）。

## 核心特性

- **三层意图识别置信度体系**：L1 正则短路（命中 0.9，阈值 0.85）→ L2 关键词（封顶 0.79，永不单独短路）→ L4 LLM JSON 置信度兜底；`ConflictResolver` 归一化加权合并（权重 1.0/0.7/0.9）
- **三种执行模式**：ReAct（Thought→Action→Observation，硬上限 10 轮）、Plan-and-Solve（规划→校验→执行→剩余步重规划 ≤2 次）、Supervisor-Worker（Supervisor 分解 SubTask，4 个专职 Worker——Monitor/Analyze/Execute/Knowledge——经 A2A 协议协同）
- **五层告警分析管线**：L0 指纹抑制 → L1 正则定级 → L2 ML 漏判救援（Tribuo 逻辑回归进程内推理，"只升不降"零回归语义）→ L3 RAG → L4 LLM 根因（L5 会诊日上限 20 次）；90%+ 重复告警在 LLM 调用前终止
- **ML 定级层评测门禁**：394 条人工标注种子数据启动训练，留出集准确率 0.821 ≥ 0.80 门禁否则自动回退旧行为；CI 强制复测种子数据质量
- **混合检索 RAG**：ES BM25 + bge-m3 向量双路召回，RRF(k=60) 客户端融合；端口契约"失败返回空表"保证检索故障不影响主链路
- **三层记忆**：进程内滑动窗口（20 条）/ Redis 中期记忆（TTL 7 天读时刷新）/ 任务级工作记忆（ReAct 步骤防重复工具调用）
- **安全门**：高危操作两段式确认，一次性令牌绑定会话 + 消息内容防重放变体；SSRF 全段拦截；审计事件异步落库隔离主链路
- **生产化**：JWT/RBAC、Webhook 通知订阅、Runbook 异步执行、六控制器分页、`/actuator/prometheus` 指标、多模型路由（DeepSeek / Moonshot 按请求特征分流）

## 架构

六模块 Maven，依赖只向下（六边形架构：端口在 domain，实现在 infrastructure）：

```
bootstrap → api → agent-core → domain → infrastructure → common
```

| 模块 | 职责 |
|---|---|
| `smart-ops-common` | 共享枚举、A2A 消息 record、异常体系 |
| `smart-ops-domain` | 领域模型 + 端口（Alert/KnowledgeEntry/LogLevelClassifier 等接口） |
| `smart-ops-infrastructure` | 端口实现：JPA、ChatService（LLM 统一入口）、ES 检索、Redis 记忆、Tribuo ML 推理、ETL |
| `smart-ops-agent-core` | 意图、路由、ReAct/Plan/Supervisor、工具、安全门、告警管线各层 |
| `smart-ops-api` | REST + SSE、GlobalExceptionHandler、JWT 过滤器 |
| `smart-ops-bootstrap` | 装配与配置、外置 Prompt 模板 |

## 快速开始

要求：JDK 17、Maven 3.9+、Docker（本地依赖）。

```bash
# 1. 启动本地依赖（MySQL 8 + Redis 7 + Elasticsearch 8）
docker compose up -d

# 2. 配置 LLM Key
export DEEPSEEK_API_KEY=sk-...

# 3. 启动后端（端口 8080）
mvn install -DskipTests
cd smart-ops-bootstrap && mvn spring-boot:run

# 4. 启动前端
cd frontend && npm install && npm run dev
```

所有 RAG/Embedding 开关默认关闭，无 Ollama/ES 也能启动；MCP 默认连 `http://localhost:9090`（`PROMETHEUS_MCP_URL` 可改）。

## 测试与质量门禁

```bash
mvn test      # 单元测试（JUnit 5 + Mockito + H2 切片）
mvn verify    # 测试 + JaCoCo 覆盖率门禁（不达标构建失败）
```

覆盖率门禁：domain / agent-core 行 ≥95% 分支 ≥90%；infrastructure 92%/86%；api 70%/50%。ML 种子数据质量本身是测试——CI 用内置种子重训并断言留出集准确率达标。

## 生产部署

```bash
cd deploy && cp .env.example .env   # 填入密钥（MOONSHOT/DEEPSEEK Key、MySQL/Redis 密码、JWT Secret、admin 密码）
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

- 新表必须先在 MySQL 执行 `docs/sql/V*.sql` 再启动（prod `ddl-auto=validate`）
- 紧急关停 ML 层：`LOGWATCH_ML_ENABLED=false`；关停日志采集：`LOGWATCH_ENABLED=false`
- 每次 `--build` 后 `docker image prune -f` 防镜像层堆积（生产曾因 40G 磁盘写满引发连锁事故）

## 关键数字

| 数字 | 含义 |
|---|---|
| 6 / 50+ / 18 / 14 | Maven 模块 / REST API / MySQL 表 / 版本化 SQL 迁移 |
| 10 / 2 | ReAct 最大轮次 / Plan 最大重规划次数 |
| 0.9 / 0.85 / 0.79 | L1 命中分 / 意图短路阈值 / L2 置信度上限 |
| 394 / 0.821 / 0.80 / 0.9 | ML 种子样本 / 留出集准确率 / 启用门禁 / prod 采信置信度 |
| 2000 / 256 | 采集队列 / SSE 缓冲 |
| 10次/分 / 20次/日 | L4 LLM 限流 / L5 会诊上限 |

## 文档导航

- `agent.md` — 开发章程（阶段门禁、代码风格、提交格式）
- `智能运维Agent项目复现规划.md` — 需求权威来源
- `docs/decisions/` — ADR-001 ~ ADR-019 架构决策记录
- `docs/solutions/` — 非平凡问题排查记录
- `docs/sql/` — V1–V14 手工版本化迁移脚本
