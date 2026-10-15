# 配置、数据库初始化与 CLI

CLI 已迁移至 Go/pgx，Gateway 与 Java 客户端保持 Java/JDBC。项目概览见[README](../README.md)，Go CLI 完整构建及配置规则见 [cli/README](../cli/README.md)。

## Java / 网关配置

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/pg_eventbus
    username: app_role
    password: change-me
    driver-class-name: org.postgresql.Driver
bus:
  schema: bus
  batch-size: 100
  lock-ttl-seconds: 30
```

以上是 Gateway 配置示例；Gateway 的 `bus` 配置只包含 schema、批大小和锁 TTL。默认值来自其配置类及 [application.properties](../gateway/src/main/resources/application.properties)：

| 属性 | 默认值 | 环境变量 / 作用 |
|---|---|---|
| `bus.schema` | bus | `BUS_SCHEMA`；数据库函数所在 schema |
| `bus.batch-size` | 100 | `BUS_BATCH_SIZE`；未提供正数 limit 时的批大小 |
| `bus.lock-ttl-seconds` | 30 | `BUS_LOCK_TTL_SECONDS`；获取锁的 TTL（秒） |

Java 直连客户端使用自己的 [PgEventBusProperties](../client/java/db/src/main/java/me/asu/pg_eventbus/core/PgEventBusProperties.java)，由宿主注册配置绑定及服务，其默认值如下。接入时须显式设置 `bus.enabled=true`、正数批大小、锁 TTL，以及使用重试服务时的最大重试数：

| 属性 | 默认值 | 环境变量 / 作用 |
|---|---|---|
| `bus.enabled` | false | `BUS_ENABLED`；启用仓储和核心服务 |
| `bus.schema` | bus | `BUS_SCHEMA`；数据库函数所在 schema |
| `bus.consumer-name` | null | `BUS_CONSUMER_NAME`；当前公开方法仍显式接收消费者名称 |
| `bus.batch-size` | 0 | `BUS_BATCH_SIZE`；Java 拉取/重试未提供正数 limit 时的批大小 |
| `bus.lock-ttl-seconds` | 0 | `BUS_LOCK_TTL_SECONDS`；Java 拉取/回调/重试获取锁的 TTL |
| `bus.max-retries` | 0 | `BUS_MAX_RETRIES`；Java 重试服务的失败阈值 |

`bus.enabled`、`bus.consumer-name` 和 `bus.max-retries` 属于 Java 直连客户端，Gateway 不使用它们。schema 的空白值回退到 `bus`。调用会引用并转义标识符；Go CLI 初始化要求 schema 和授权角色符合 `[A-Za-z_][A-Za-z0-9_]*`，随后按标识符引用以保留大小写。

Java 配置不读取 `PGEVENTBUS_SCHEMA`。自定义 schema 使用 `bus.schema: analytics` 或 `BUS_SCHEMA=analytics`，初始化与运行时必须保持一致。

Gateway 根据 `spring.datasource.url` 创建数据库 Bean。Go CLI 自己加载配置、创建原生 pgx 连接；两者都不依赖客户端或其它项目模块，也不使用 ORM。

## Go CLI 配置

CLI 使用 `pg-eventbus.toml`，通过 `[database]` 配置 url/user/password/schema，通过 `[consumer]` 配置 batch_size/lock_ttl_seconds，顶层 timeout 可设为 `30s` 等 Go duration。完整示例见 [pg-eventbus.example.toml](../cli/pg-eventbus.example.toml)。默认 schema bus、批大小 100、TTL 30 秒。

优先级为选项 > 环境变量 > TOML > 默认值。文件选择为 `--config` > `PGEVENTBUS_CONFIG` > 当前目录的 pg-eventbus.toml；只有默认文件可缺省。环境变量使用 `PGEVENTBUS_DATABASE_URL`、`PGEVENTBUS_DATABASE_USER`、`PGEVENTBUS_DATABASE_PASSWORD`、`PGEVENTBUS_SCHEMA`、`PGEVENTBUS_BATCH_SIZE`、`PGEVENTBUS_LOCK_TTL_SECONDS`、`PGEVENTBUS_TIMEOUT`；参数为 `--database-url`、`--database-user`、`--database-password`、`--schema`、`--batch-size`、`--lock-ttl-seconds`、`--timeout`。URL 必须为带主机的 `postgresql://` 或 `postgres://`，没有默认数据库 URL。

TOML 文件最大 1 MiB，键区分大小写；批大小和 TTL 必须为正 int32，timeout 为非负 Go duration。单独用户名/密码可覆盖同级或低优先级 URL 内的凭证；低优先级值仅在高优先级 URL 缺少对应凭证时补充，显式空密码也会生效。完整映射和参数写法见 [CLI README](../cli/README.md)。

`--timeout 30s` 可设置整次操作时限，默认 0 不增加总时限；连接建立默认最多 10 秒，可通过 URL 的 connect_timeout 调整。Ctrl+C 取消请求。帮助不连接数据库。运行时不依赖 JVM、psql、其它 module 或外部 SQL 文件。

CLI 不再读取 Spring/BUS 环境映射、application.* 或 YAML/properties，不再提供 driver、JDBC URL 和点分参数兼容。旧环境作为无关环境忽略，显式指定旧格式文件或旧参数会报错。TOML 未知键、重复键、类型错误和语法错误均失败且诊断不泄露文件内容；不展开占位符。Java Gateway/客户端的配置保持原样。

## Go / Python 直连配置

schema 的优先级为：构造函数显式值、`PGEVENTBUS_SCHEMA`、`bus`。

Go 使用 `NewWithSchema("analytics")`；Python 使用 `PgEventBusClient(schema="analytics")`。它们通过子进程执行 SQL，不使用 Java 的 JDBC URL。

命令解析顺序：

1. Python 构造参数 `command`（Go 无对应公开参数）。
2. `PGEVENTBUS_PSQL_COMMAND`。
3. PATH 上的 `psql`。
4. 指定工具目录：Python `tool_dir` 优先于 `PGEVENTBUS_PGSQL_CLIENT_DIR`；Go 使用该环境变量。
5. 未指定工具目录时，从当前工作目录逐级向父目录寻找 `tools/pgsql-client`。

工具目录中的候选程序为 `bin/psql-go.exe` 或 `bin/psql-go`，不是任意目录中的标准 `psql`。未找到时仍尝试执行 `psql`。命令值应为可执行文件路径，不能附带命令行参数。

客户端向命令传入 `-q -t --no-psqlrc -c SQL`。标准 `psql` 连接参数可通过 `PGHOST`、`PGPORT`、`PGDATABASE`、`PGUSER` 等环境配置；自定义程序需支持这些参数。当前源码限制见[客户端指南](pg-eventbus-client-guide.md)。

## 数据库初始化

手动安装时，先将 docs/sql 的编号脚本复制到安装目录，再修改副本中 `001_bootstrap.sql` 开头的 schema 创建及搜索路径语句，并在同一会话按编号执行，例如：

```sql
CREATE SCHEMA IF NOT EXISTS analytics;
SET search_path = analytics, pg_temp;
```

CLI 的合并模板则使用 `BEGIN/COMMIT` 和事务内 `SET LOCAL`。表、函数及函数体中的对象引用均不带 schema 前缀；函数通过 `SET search_path FROM CURRENT` 保存安装时的搜索路径，因此调用方无需设置相同路径。`pg_catalog` 隐式优先查找，`pg_temp` 显式放在末尾。安装 schema 仅应允许可信的 DDL 账号创建对象。

手动执行前，还需将副本 `990_grant_access.sql` 中 v_role 的 app_role 字符串改为已存在的应用角色；授权和分区检查自动使用选定 schema。先执行 001，再按数字顺序执行各函数文件，最后 990 授权；所有文件使用同一连接，建议外围事务及遇错停止。不要修改仓库内生成锚点来适配某个部署，完整顺序和 psql 示例见 [SQL 来源说明](sql/README.md)。CLI 安装使用 TOML 的 database.schema、`--schema` 或 `PGEVENTBUS_SCHEMA`，运行时也应使用相同名称。

先创建数据库和授权角色，再用具有 DDL 和授权权限的账号配置数据源并执行：

```powershell
$cli = '.\cli\bin\pg-eventbus-cli.exe'
& $cli db init app_role
```

角色参数省略时读取 `PGEVENTBUS_DB_OWNER`。名称虽然在代码中称为 owner，实际只替换 SQL 的授权对象 `app_role`，不创建角色、不转移对象所有权。函数和对象归执行 DDL 的账号所有。

CLI 使用 `go:embed` 内嵌 `cli/sql/init.sql` 生成模板，只渲染 schema/owner 标记，通过同一连接的 simple protocol 完整执行事务脚本，不按分号拆分。失败时尝试同连接 ROLLBACK，然后关闭连接。只在 docs/sql 修改编号来源，再在 cli/ 运行 `go generate ./sql` 和 `go run ./internal/cmd/gensql -source ../docs/sql -output sql/init.sql -check`，同时提交来源和生成物；不直接修改 init.sql。Go build 不自动生成，CLI 运行时也不访问 docs/sql，复制到仓库外仍自带 SQL。未来版本化管理另见 [Flyway 方案](flyway-adoption.md)。

`db init` **不预建分区**。需要立即预建时：

```powershell
& $cli partition ensure-current
& $cli partition ensure-future
```

发布函数也会确保当前月份分区存在。合并脚本使用 `IF NOT EXISTS` 和 `CREATE OR REPLACE FUNCTION`，但不是版本化 schema 迁移工具。

## CLI 命令

在 `cli/` 使用 Go 构建可执行文件，详见 [CLI 构建说明](../cli/README.md)。以下均为可执行文件后的位置参数。

| 参数 | 行为 |
|---|---|
| `db init [owner]` | 安装数据库对象并授权；省略角色时读取环境变量 |
| `partition ensure-current` | 确保当前月份分区 |
| `partition ensure-future` | 确保未来 6 个月分区 |
| `partition drop-older-than <keepMonths>` | 调用旧分区删除函数 |
| `event send\|publish <source> <target> <type> <payloadJson>` | 发布 JSON |
| `event get\|poll <consumerName> <instanceId> [limit]` | 获得租约后拉取 |
| `event set-cursor\|commit <consumerName> <instanceId> <newLastId>` | 提交位点 |
| `event recover <consumerName> <isoInstant>` | 按时间重设位点；要求带时区的 RFC3339，可含纳秒 |
| `maintenance run` | 执行数据库维护函数 |

```powershell
& $cli event send source-a consumer-a price-update '{}'
& $cli event get consumer-a instance-a 100
# 替换为已成功处理的末条事件 ID，且须在锁到期前提交
& $cli event set-cursor consumer-a instance-a 12345
# 此命令会重设位点，应在需要恢复时执行
& $cli event recover consumer-a 2026-10-01T00:00:00Z
```

JSON 参数须完整作为一个 shell 参数传入；不同 PowerShell 版本对原生程序引号的传递有差异，复杂负载可通过网关发送。事件/维护成功命令向 stdout 输出单行 JSON，时间为旧 CLI 的数值型 epoch seconds、保留纳秒精度；`db init` 保留完成文本。诊断写 stderr。退出码：0 成功/帮助（包括租约失败、commit=false 结果），1 连接/SQL/文件等运行错误，2 参数/配置错误，130 Ctrl+C。未知命令和缺参不再返回伪成功。

CLI 的 poll 先获取租约，再拉取事件；limit 省略或非正数时使用配置批大小，显式 limit 须为 int32。poll 和 commit 均不释放租约；再次 poll 需要等待租约到期。`keepMonths` 必须为非负 int32，提交位点为 int64。CLI 不提供续租、释放锁、重试处理或 cleanupConsumed 命令。

## 维护

`maintenance run` 固定调用 `ensure_future_partitions(6)` 和 `drop_partition_older_than(3)`。保留阈值为当前月份月初减去 3 个月，删除函数意图删除早于该月份的整个分区，独立于消费者进度。

`cleanupConsumed` 在 Java / HTTP 客户端及网关提供，CLI 没有单独命令。当前 SQL 依据所有已注册消费者的最小位点选择 `id < min(last_event_id)` 的批次，默认批大小 10000。

仓库没有维护或重试定时任务，需由调用方调度。清理的跨分区 `ctid` 匹配存在实现边界，详见[SQL 文档](PG-EventBus.md#当前实现限制)。
