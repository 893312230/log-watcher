# ADR-014：多层记忆分层——短期窗口 + 中期 Redis 持久化 + 执行器工作记忆，长期记忆延期

## 背景

阶段四任务8要求"多层记忆系统"。当前仅存在短期记忆：`MessageWindowChatMemory`（滑动窗口 20 条）+ `BoundedChatMemoryRepository`（LRU 内存上限 1000 会话，见修复期 S1），进程重启即丢失。`spring-boot-starter-data-redis` 依赖已声明但未使用。规划原文提到"中期/长期/工作记忆"，需要明确各层的最小诚实形态，避免无需求驱动的过度工程。

## 决策

1. **短期记忆**：维持现状（窗口 20 条 + 有界内存仓库），不改动。
2. **中期记忆 = Redis 持久化的会话记忆**：新增 `RedisChatMemoryRepository`（实现 Spring AI `ChatMemoryRepository`），会话消息写入 Redis 并带 TTL（默认 7 天，读时刷新）。语义：会话跨重启存活、自动过期。由 `smartops.memory.mid-term.enabled`（默认 false）切换 Redis/有界内存两实现。**不做 LLM 摘要压缩**——无评测数据证明窗口截断是实际问题，留待评测驱动。
3. **工作记忆 = 执行器级 TaskScratchpad**：新增 `WorkingMemory` 接口（按 conversationId 隔离的有界 KV），`ReActExecutor`/`PlanAndSolveExecutor` 在执行期间写入中间 Thought/步骤结果，任务结束清理。解决多步执行中中间态无处安放的问题；进程内即可，不持久化。
4. **长期记忆（用户画像/偏好）延期**：当前系统无用户体系、无画像消费需求，实现即为 speculative generality。待有真实消费场景（如个性化回答）时新增 ADR 引入。

## 备选方案

- **中期记忆做 LLM 摘要压缩**：压缩质量无评测基线，且窗口 20 条对运维对话已够用，放弃（评测驱动再议）。
- **工作记忆持久化到 Redis**：工作记忆生命周期=单次任务执行，跨进程无意义，放弃。
- **长期记忆用向量库存用户偏好**：无用户体系与消费场景，放弃。

## 影响

- 新增配置键 `smartops.memory.mid-term.enabled/ttl-days`、`smartops.memory.working.enabled/max-entries`，均带注释；默认仅工作记忆开启，其余关闭时应用行为与现状一致
- `RedisChatMemoryRepository` 需处理 Spring AI `Message` 的 Jackson 多态反序列化（实现时验证，必要时自定义 mixin）
- 测试策略：纯 Mockito（mock `RedisTemplate`）+ 环境变量门控薄 IT（`SMARTOPS_IT=true`），与 ADR-016 测试策略一致
