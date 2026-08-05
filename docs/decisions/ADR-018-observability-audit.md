# ADR-018：可观测性与 L2 操作审计——统一门面、异步落库、钩子埋点

## 背景

阶段五生产化要求：关键操作可观测（指标）+ 可审计（L2 操作审计落库）。
被观测的操作有四大类：LLM 调用、工具调用、任务执行（路由边界）、安全决策。
生产服务器 2C/1.8G，观测通道必须轻量、有界，且任何情况下不得影响业务主链路；
生产 `spring.jpa.hibernate.ddl-auto=validate`，新表 `audit_event` 需手工迁移。

## 决策

1. **指标 + 审计双通道统一出口 Observability 门面**。各钩子
   （ChatService/ToolCallingManager/AgentRouter/SecurityGate）不直接处理
   MeterRegistry 与 AuditRecorder 的可空依赖，统一调用 Observability 的
   四个 record 方法；门面内部对任一通道缺失或失败静默跳过，绝不向业务链路抛异常。
2. **审计异步落库，有界队列背压**。AuditRecorder 端口契约即"尽力投递、
   不阻塞、不抛出"；实现 AsyncAuditRecorder 用 ArrayBlockingQueue（默认 2000）
   + 单后台守护线程落库，队列满丢弃并计数（指标 smartops.audit.queue.dropped 可观测），
   停止时 drain 剩余事件。审计是高价值低优先数据，丢事件优于阻塞业务。
3. **钩子埋点选择"汇聚点"而非逐方法**。LLM 观测挂在 ChatService.invokeLlm
   （全部 chat* 方法的唯一汇聚点，含异常包装）；工具观测以
   ObservedToolCallingManager 装饰 DefaultToolCallingManager，挂入
   ToolCallingAdvisor——MCP 自动注册工具与 @Tool Bean 全部经此边界，
   一次埋点全覆盖；任务观测挂在 AgentRouter.route 边界（含意图失败、
   安全拦截、执行器异常五条路径）；安全决策仅记录高危操作的放行/拦截，
   低危只读不记录以避免审计噪音。
4. **已知盲区：L4LLMRecognizer 不经 ChatService**（直接用 ChatClient 做意图识别），
   其调用不产生 LLM_CALL 审计。该调用属高频低价值的元调用，且改造需动意图管线，
   本期不动，后续如需再补。
5. **不引入分布式追踪**。2C/1.8G 无 collector 部署空间；以 traceId
   （conversationId / logwatch 指纹前缀）作为审计事件关联键，
   idx_audit_trace 索引支撑单链路操作回溯。
6. **指标导出 = Micrometer + /actuator/prometheus**。不引额外 APM；
   审计队列与分析队列（logwatch）的容量指标以 MeterBinder Gauge 导出，
   由 Actuator 自动绑定，随 Prometheus 端点暴露。
7. **生产建表 = 手工迁移脚本**（沿用 ADR-017 §3）。交付
   `docs/sql/V2__audit_event.sql`，须在应用启动前执行；
   实体 AuditEventEntity 与脚本一一对应。

## 后果

- 业务主链路零阻塞、零异常扩散：观测全部静默降级。
- 审计查询入口：`GET /api/audit/events`（类型/traceId/时间范围过滤，时间倒序）。
- 指标入口：`/actuator/prometheus`（smartops.llm.calls / smartops.tool.calls /
  smartops.task.executions / smartops.security.decisions + 两个队列 Gauge）。
- 高峰期审计可能丢弃（有界队列），以 dropped 指标监控并可调
  `smartops.audit.queue-capacity` 扩容。
