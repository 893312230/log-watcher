# 数据库迁移脚本说明

本目录存放全部手工迁移脚本（非 Flyway，需人工按序执行）。生产环境
`spring.jpa.hibernate.ddl-auto=validate`，实体与脚本必须一一对应，
**上线新版本前必须先执行对应脚本，否则应用启动校验失败**。

## 执行方式

```bash
# 按版本号升序逐个执行（已存在的表有 IF NOT EXISTS 保护，可重复执行）
mysql -h <host> -u <user> -p <database> < docs/sql/V6__runbook.sql
```

## 版本对照

| 脚本 | 表 | 引入阶段 |
|---|---|---|
| V1__alert_record.sql | alert_record（告警） | 阶段五 |
| V2__audit_event.sql | audit_event（操作审计） | 阶段五 |
| V3__server_config.sql | server_config（应用服务） | 阶段六 |
| V4__knowledge_entry.sql | knowledge_entry（知识库） | 阶段六 |
| V5__topology.sql | topology_node / topology_edge（服务拓扑） | 阶段七 |
| V6__runbook.sql | runbook / runbook_step / runbook_execution / runbook_step_result | 阶段十二 S1 |
| V7__notification_channel.sql | notification_channel（通知渠道） | 阶段十二 S1 |
| V8__silence_window.sql | silence_window（告警静默窗口） | 阶段十二 S1 |
| V9__integration.sql | integration（外部集成） | 阶段十二 S1 |
| V10__slo.sql | slo（服务等级目标） | 阶段十二 S1 |
| V11__oncall_rotation.sql | oncall_rotation（值班轮换） | 阶段十二 S1 |
| V12__webhook.sql | webhook_subscription / webhook_delivery（Webhook 订阅与投递日志） | 阶段十二 S5 |
| V13__user.sql | sys_user（登录用户，角色 ADMIN/VIEWER） | 阶段十二 S6 |
| V14__alert_record_indexes.sql | alert_record 索引 (created_at)、(source, created_at) | 阶段十三 WS5 |

## 阶段十二升级指引（V6–V13）

从阶段十一升级到阶段十二时，按序执行 V6 → V13 共 8 个脚本：

```bash
for f in docs/sql/V{6,7,8,9,10,11,12,13}__*.sql; do mysql -h <host> -u <user> -p <database> < "$f"; done
```

注意事项：

1. **初始管理员**：V13 只建表不插数据；应用首次启动时若 `sys_user`
   为空会自动写入 admin 账号，初始密码取环境变量
   `SMARTOPS_ADMIN_PASSWORD`（默认 `admin123`，**生产必须覆盖并尽快修改**）。
2. **JWT 密钥**：生产必须设置 `SMARTOPS_JWT_SECRET`（≥32 字节），
   否则使用开发默认值。
3. **静态 Token 兼容**：机器调用仍可继续使用 `SMARTOPS_API_TOKEN`，
   具备 ADMIN 等价权限；浏览器端走登录签发的 JWT（24 小时过期）。
4. **内存数据迁移**：阶段十一及之前的 Runbook/通知渠道/静默窗口等
   仅存内存，升级后需通过前端页面或 API 重新录入（无自动迁移）。
