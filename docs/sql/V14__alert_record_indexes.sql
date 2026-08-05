-- V14：alert_record 查询索引（阶段十三 WS5 性能优化）
-- 背景：告警列表按 created_at 倒序分页，并按 source 过滤；
--       数据量增长后全表扫描 + filesort 成为瓶颈。
-- 执行：mysql -h <host> -u <user> -p <database> < docs/sql/V14__alert_record_indexes.sql
-- 注意：MySQL 8 不支持 CREATE INDEX IF NOT EXISTS，重复执行会报 1061（键已存在），可忽略。

CREATE INDEX idx_alert_record_created_at ON alert_record (created_at);
CREATE INDEX idx_alert_record_source_created_at ON alert_record (source, created_at);
