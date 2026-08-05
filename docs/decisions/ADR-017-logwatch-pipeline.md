# ADR-017：logwatch 日志采集分析告警——只读采集、手工迁移、分层降级

## 背景

阶段五 logwatch 要求闭环：采集本机日志（文件路径 + 运行中 jar 进程）→ 关键字检出 →
四层分析（L0 抑制 → L1 定级 → L2 RAG → L3 LLM → L4 会诊）→ 告警（MySQL 落库 +
REST 查询 + SSE 推送）。生产服务器 2C/1.8G，采集与分析必须轻量有界；
生产 `spring.jpa.hibernate.ddl-auto=validate`，新表 `alert_record` 不能依赖 Hibernate 自动建表。

## 决策

1. **采集只读，不过 SecurityGate**。FileTailLogSource 仅以只读方式 `RandomAccessFile("r")`
   打开日志文件；JpsProcessLocator 仅执行 `jps -l` 与读取 `/proc`（符号链接解析），
   不发起任何写操作或进程控制。SecurityGate（ADR-010）保护的是高风险变更操作
   （重启/扩容/改配置），只读采集不在其语义范围内，故不接入。
2. **jar 进程定位三级降级**：`jps -l` 匹配 jar 路径 → 遍历 `/proc/*/cmdline` →
   读 `/proc/<pid>/fd/1|2` 符号链接定位日志文件。任一级失败返回 empty，
   JarProcessLogSource 按周期重试，不影响其他采集源；Windows 仅支持 file 源
   （无 `/proc`，定位恒为空，仅周期性重试日志，不报错）。
3. **生产建表 = 手工迁移脚本**。prod 保持 `ddl-auto=validate` 不动，交付
   `docs/sql/V1__alert_record.sql` 随发布在应用启动前手工执行；实体
   `AlertRecordEntity` 与脚本一一对应（注释互相引用）。不引入 Flyway——
   当前仅一张表，引入迁移框架的收益抵不过其运行成本。
4. **SSE 告警推送直通，沿用 ADR-012**。`/api/alerts/stream` 与对话 SSE 一致：
   Reactor Flux 持续流、15s 心跳注释行、不经过路由/意图管线、不发送 [DONE]。
   SseAlertNotifier 常驻组件（不随 `smartops.logwatch.enabled` 条件化），
   避免开关关闭时 API 层注入缺失导致启动失败。
5. **分析分层降级链**：L2 无 `KnowledgeRetriever` Bean（ES 未开启）自动跳过；
   L3 分钟级限流或 `LlmCallException` → 以 L1 定级结果降级落库并标注；
   L4 日上限或会诊失败 → 保留 L3 结论追加降级标注。任何一层异常由管线捕获后继续，
   单事件失败不影响队列消费。
6. **小内存边界**：采集 1 线程/源、分析单线程、队列容量 2000（满则丢弃计数）、
   SSE 背压缓冲 256、L3 默认 10 次/分钟、L4 默认 20 次/日。
7. **自引用告警必须显式排除**（生产 E2E 实测教训 2026-07-22）：采集应用自身日志时，
   管道各层（Supervisor/Worker/logwatch）的 INFO 日志会回显告警内容（含 "ERROR"/
   "Exception" 字样），被关键字匹配器再次命中形成反馈循环——分析线程被自身告警
   占满，真实告警排队积压。对策：`smartops.logwatch.exclude-keywords` 配置
   排除子串（按包名前缀），匹配器命中排除子串优先放行不告警；prod 默认排除
   `c.s.agent.{logwatch,orchestrator,worker}` 与 `c.s.i.logwatch`。

## 备选方案

- **引入 Flyway/Liquibase 管理迁移**：单表场景过重，保留为表数量增长后的演进路径。
- **logwatch 相关 Bean 全部条件化（含 SseAlertNotifier 与 Controller）**：
  开关关闭时端点消失更"干净"，但条件化 Controller 会让开关行为对 API 消费者不可预期，
  且 AlertRepository 持久层无条件化需求（表已随迁移存在），放弃。
- **采集接入 SecurityGate 审计**：采集不写任何系统状态，接入只会增加确认噪音，放弃。
- **jar 源用 JVM Attach API 读取日志配置**：需目标进程开启 attach 且侵入性强，放弃。

## 影响

- 部署顺序约束：prod 升级含 logwatch 时，必须先执行 `V1__alert_record.sql` 再启动新容器，
  否则 validate 失败应用无法启动
- 新增配置键 `smartops.logwatch.*`（application.yml 全中文注释，`enabled` 默认 false）
- 新增端点：`GET /api/alerts`（分页过滤）、`GET /api/alerts/{id}`、
  `POST /api/alerts/{id}/ack`、`GET /api/alerts/stream`（SSE）
- 采集状态目录 `state-dir` 需持久化（容器部署时挂载卷），否则重启从头/从尾重采
- LLM 费用有三道闸：L0 合并削减 + L3 分钟限流 + L4 日上限
