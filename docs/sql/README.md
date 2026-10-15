# SQL 来源、顺序与维护

本目录是当前初始化 SQL 的唯一维护来源，按功能拆分，可用于人工安装。`cli/sql/init.sql` 是由这些源文件生成并提交的 CLI 安装模板，运行时内嵌在 Go 程序中。不要直接修改生成文件。

三位数字表示安装顺序，不表示数据库升级版本。当前没有 Flyway 或 schema history，不能根据编号判断某个数据库已完成哪些升级。

| 顺序 | 文件 | 职责 |
|---|---|---|
| 001 | [001_bootstrap.sql](001_bootstrap.sql) | 创建/选择 schema，创建表与索引 |
| 010 | [010_register_consumer.sql](010_register_consumer.sql) | 注册消费者 |
| 020 | [020_create_month_partition.sql](020_create_month_partition.sql) | 创建指定月份分区 |
| 030 | [030_ensure_current_partition.sql](030_ensure_current_partition.sql) | 确保当前分区 |
| 040 | [040_ensure_future_partitions.sql](040_ensure_future_partitions.sql) | 预建未来分区 |
| 050 | [050_acquire_consumer_lock.sql](050_acquire_consumer_lock.sql) | 获取租约 |
| 060 | [060_renew_consumer_lock.sql](060_renew_consumer_lock.sql) | 续租 |
| 070 | [070_release_consumer_lock.sql](070_release_consumer_lock.sql) | 释放租约 |
| 080 | [080_commit_consumer_offset.sql](080_commit_consumer_offset.sql) | 提交位点 |
| 090 | [090_init_consumer_at_time.sql](090_init_consumer_at_time.sql) | 按时间初始化位点 |
| 100 | [100_publish_event.sql](100_publish_event.sql) | 发布事件 |
| 110 | [110_poll_events.sql](110_poll_events.sql) | 拉取事件 |
| 120 | [120_poll_retry.sql](120_poll_retry.sql) | 拉取到期重试 |
| 130 | [130_poll_deadletter.sql](130_poll_deadletter.sql) | 查询死信 |
| 140 | [140_schedule_retry.sql](140_schedule_retry.sql) | 安排重试 |
| 150 | [150_move_to_deadletter.sql](150_move_to_deadletter.sql) | 写入死信 |
| 160 | [160_delete_retry.sql](160_delete_retry.sql) | 删除重试记录 |
| 170 | [170_cleanup_consumed.sql](170_cleanup_consumed.sql) | 按消费进度清理 |
| 180 | [180_cleanup_older_than.sql](180_cleanup_older_than.sql) | 按时间清理 |
| 190 | [190_drop_partition_older_than.sql](190_drop_partition_older_than.sql) | 删除旧分区 |
| 200 | [200_maintenance.sql](200_maintenance.sql) | 组合维护函数 |
| 990 | [990_grant_access.sql](990_grant_access.sql) | 最后授权 |

这些文件定义函数，不会在安装时执行清理、删除分区或预建分区。实际维护由之后的函数调用触发。

## 生成 CLI 内嵌 SQL

在仓库中的 `cli/` 目录运行：

```powershell
$env:GOTOOLCHAIN = 'local'
$env:GOPROXY = 'off'
go generate ./sql
go run ./internal/cmd/gensql -source ../docs/sql -output sql/init.sql -check
```

`go generate` 只拼接、转换 SQL 文本，不连接数据库或执行 SQL。生成器按三位编号排序，拒绝非法名称、重复编号、错误首尾、源文件中的 CLI 标记及缺失/重复的安装锚点。修改源 SQL 后，生成并检查，把源文件和 `cli/sql/init.sql` 一起提交。增加脚本时使用未占用编号，放到依赖之后、990 授权之前；不需要另外编辑顺序清单。

Go build **不会自动运行 go generate**。完整仓库构建应先生成/检查再编译；只复制 `cli/` 时可使用已经提交的生成物直接构建，不需要 docs/sql 或生成动作。检查模式只比较文件内容，发现生成物缺失/过期时非零退出，可用于未来 CI；它不是 SQL 语法或数据库集成验证。

生成器在现有 `BEGIN/COMMIT` 包装中，只做以下安装适配，业务 SQL 保持源文件定义：

- 001 的 `CREATE SCHEMA IF NOT EXISTS bus;` 转为 schema 标记；`SET search_path = bus, pg_temp;` 转为事务内 SET LOCAL 与同一 schema 标记。
- 990 的 `v_role TEXT := 'app_role';` 转为角色字符串标记，授权仍使用 format(%I) 引用角色。

这些是生成器的明确锚点。需要修改默认 schema 或安装参数结构时，一并调整生成器规则；锚点不匹配会失败，避免静默生成错误模板。生成器先完整校验和组装，再写同目录临时文件并替换目标；失败不会用半份 SQL 覆盖旧产物。CLI 执行时按各自 SQL 语境引用 schema 和角色，不是简单替换任意业务字符串。

## 安装与执行顺序

推荐使用 Go CLI 的 `db init <role>`，schema 来自 TOML 的 database.schema、`--schema` 或 `PGEVENTBUS_SCHEMA`。数据库与授权角色须提前创建，安装账号需要 DDL 和授权权限。该命令完整执行内嵌事务脚本。

人工安装时，先将本目录复制到临时安装目录，在副本中修改 001 的两处 bus 标识符和 990 的 v_role 字符串，再按文件名排序执行。不要为了某个部署修改仓库内的固定生成锚点。角色字符串中的单引号按 SQL 规则写为两个单引号；schema 标识符需要时使用双引号。

所有文件必须在**同一数据库连接**执行，001 设定的 search_path 才能用于后续对象创建，以及函数的 SET search_path FROM CURRENT。不要每个文件启动一次 psql，也不要直接按文件系统返回顺序执行。

例如在 PowerShell 中，连接参数先通过 PGHOST/PGDATABASE/PGUSER 等环境变量设置，然后对已准备的副本使用同一个 psql 进程：

```powershell
$installDir = 'D:\temp\pg-eventbus-sql' # 已复制并修改参数的安装副本
$psqlArgs = @('--no-psqlrc', '-v', 'ON_ERROR_STOP=1', '--single-transaction')
Get-ChildItem -LiteralPath $installDir -Filter '*.sql' |
    Sort-Object Name |
    ForEach-Object { $psqlArgs += @('-f', $_.FullName) }
& psql @psqlArgs
if ($LASTEXITCODE -ne 0) { throw 'SQL installation failed' }
```

源文件本身不包含外围 BEGIN/COMMIT；示例由 psql 包装一个事务并遇错停止。已有对象的 IF NOT EXISTS 不等于数据库升级或结构一致性检查；本流程主要用于初始化/重装函数，不能替代已有数据的版本化迁移。

未来接入迁移管理时，遵循 [Flyway 接入方案](../flyway-adoption.md)，不要直接将这里的 001/010/990 改称数据库版本。
