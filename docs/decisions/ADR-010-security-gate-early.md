# ADR-010：安全控制提前——最小安全门与一次性确认令牌

## 背景

原规划将全部安全控制（L0 输入过滤 ~ L3 人工确认）推迟到阶段五，但阶段三的 ExecuteAgent 已具备重启/扩缩容/配置变更能力，形成"能执行高危操作却无任何防护"的窗口期（规划审查 P0-3）。同时 `SecurityLevel` 枚举与 `SecurityViolationException` 长期处于死代码状态。

## 决策

1. **安全门提前落地**：新增 `SecurityGate`（agent-core/security），按关键词将操作文本分为 HIGH（重启/扩缩容/配置变更/删除/部署/停机/回滚/迁移）与 LOW（只读）两档。ExecuteAgent 执行前调用 `checkPermitted`，HIGH 且未确认时抛出 `SecurityViolationException`（错误码 `SECURITY_CONFIRM_REQUIRED`，激活既有死代码）。
2. **一次性确认令牌流**：API 层捕获安全违规 → `ConfirmationTokenStore` 签发令牌（绑定会话 ID + 触发消息，TTL 10 分钟，容量 10000 上限 LRU 淘汰）→ 返回 `pendingConfirmation=true` + 令牌 → 客户端携带令牌与原始消息重提 → 验证通过即消费（防重放），`ConfirmationContext`（ThreadLocal）标记当前线程已确认 → 门放行。
3. **异常类型化传播**：`AbstractWorkerAgent` 与 `AgentRouter` 不再把 `SecurityViolationException` 吞为普通失败响应，改为重抛给 API 层处理。
4. **DTO 扩展**：`ChatRequest` 新增 `confirmationToken`（可选，向后兼容）；`ChatResponse` 新增 `pendingConfirmation` / `confirmationToken`。

## 备选方案

- **等到阶段五做完整四级安全模型**：窗口期内任何"重启 XX"的自然语言请求都会无确认执行，风险不可接受，放弃。
- **令牌只绑定会话不绑定消息**：用户可在确认 A 操作后用同一令牌执行 B 操作（消息篡改），放弃；现实现要求重提消息与触发消息完全一致。
- **确认标记显式透传（方法参数）而非 ThreadLocal**：需改动 Supervisor→Dispatcher→Worker→A2aRequest 全链路签名，侵入过大；当前 Supervisor 顺序执行子任务，ThreadLocal 足够，未来异步化时需重评。

## 影响

- 客户端高危操作变为两段式交互：首次请求返回 `pendingConfirmation=true` + 令牌，需在 10 分钟内携带 `confirmationToken` 与**相同消息**重提
- 单机内存令牌存储，重启即失效；多实例部署需替换 Redis（阶段五）
- 本门是高危操作兜底防线，不替代阶段五的 L0-L3 完整安全模型
- 集成测试：IT-2 覆盖"执行意图 → 待确认 → 确认 → 成功"全链路（`AgentControllerTest.ConfirmationFlow` + `SecurityGateFlowTest`）
