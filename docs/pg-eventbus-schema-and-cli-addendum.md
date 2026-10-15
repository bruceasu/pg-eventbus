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
  enabled: true
  schema: bus
  batch-size: 100
  lock-ttl-seconds: 30
  max-retries: 5
```

| 属性 | 默认值 | 环境变量 / 作用 |
|---|---|---|
| `bus.enabled` | false | `BUS_ENABLED`；启用仓储和核心服务 |
| `bus.schema` | bus | `BUS_SCHEMA`；数据库函数所在 schema |
| `bus.consumer-name` | null | `BUS_CONSUMER_NAME`；当前公开方法仍显式接收消费者名称 |
| `bus.batch-size` | 0 | `BUS_BATCH_SIZE`；Java 拉取/重试未提供正数 limit 时的批大小 |
| `bus.lock-ttl-seconds` | 0 | `BUS_LOCK_TTL_SECONDS`；Java 拉取/回调/重试获取锁的 TTL |
| `bus.max-retries` | 0 | `BUS_MAX_RETRIES`；Java 重试服务的失败阈值 |

Gateway 默认批大小 100、锁 TTL 30 秒；Java 直连库由宿主提供配置，`bus.enabled` 和 `bus.max-retries` 仅用于该客户端。schema 的空白值回退到 `bus`。调用会引用并转义标识符；Go CLI 初始化要求 schema 和授权角色符合 `[A-Za-z_][A-Za-z0-9_]*`，随后按标识符引用以保留大小写。

Java 配置不读取 `PGEVENTBUS_SCHEMA`。自定义 schema 使用 `bus.schema: analytics` 或 `BUS_SCHEMA=analytics`，初始化与运行时必须保持一致。

Gateway 根据 `spring.datasource.url` 创建数据库 Bean。Go CLI 自己加载配置、创建原生 pgx 连接；两者都不依赖客户端或其它项目模块，也不使用 ORM。

## Go CLI 配置

兼容 `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`、`BUS_SCHEMA`、`BUS_BATCH_SIZE`、`BUS_LOCK_TTL_SECONDS`、`PGEVENTBUS_DB_OWNER` 以及对应 `--spring.datasource.*`、`--bus.*` 选项。URL 支持原生 PostgreSQL 和原示例 JDBC 前缀；默认 schema bus、批大小 100、TTL 30 秒。

优先级为选项 > 环境变量 > 选定文件 > 默认值。`--config` 指定文件；未指定时从当前目录选择唯一的 application.yml、application.yaml 或 application.properties，存在多个则报配置错误。仅支持数据源、schema、批大小、TTL 和 PostgreSQL driver-class-name 键；Java 专用的 bus.enabled、bus.max-retries、profiles、配置导入、占位符、Hikari 属性不支持。原生 URL 由 pgx 解析，JDBC 参数白名单及文件格式边界见 CLI README。

`--timeout 30s` 可设置整次操作时限，默认 0；Ctrl+C 取消请求。帮助不连接数据库。运行时不依赖 JVM、psql、其它 module 或外部 SQL 文件。

环境中的 `SPRING_PROFILES_*`、`SPRING_CONFIG_*`、`SPRING_APPLICATION_JSON` 和未支持的 `SPRING_DATASOURCE_*` 不会静默忽略：显式设置（含空值）会在连库前返回退出码 2。数据源允许 URL、USERNAME、PASSWORD、DRIVER_CLASS_NAME，driver 只接受 org.postgresql.Driver；无关系统环境变量不受限制。

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

手动安装时，修改 `docs/sql/bootstrap.sql` 开头的 schema 创建及搜索路径语句，再在同一会话按依赖顺序执行拆分脚本，例如：

```sql
CREATE SCHEMA IF NOT EXISTS analytics;
SET search_path = analytics, pg_temp;
```

CLI 的合并模板则使用 `BEGIN/COMMIT` 和事务内 `SET LOCAL`。表、函数及函数体中的对象引用均不带 schema 前缀；函数通过 `SET search_path FROM CURRENT` 保存安装时的搜索路径，因此调用方无需设置相同路径。`pg_catalog` 隐式优先查找，`pg_temp` 显式放在末尾。安装 schema 仅应允许可信的 DDL 账号创建对象。

手动执行前，还需将授权脚本中的 `app_role` 改为已存在的应用角色；授权和分区检查自动使用选定 schema。拆分安装时，先修改 `docs/sql/bootstrap.sql` 中的 `CREATE SCHEMA` 和 `SET search_path`，再在同一连接内按依赖顺序执行其余脚本。CLI 安装继续使用 `bus.schema` / `BUS_SCHEMA` 选择 schema，运行时配置也应使用相同名称。

先创建数据库和授权角色，再用具有 DDL 和授权权限的账号配置数据源并执行：

```powershell
$cli = '.\cli\bin\pg-eventbus-cli.exe'
& $cli db init app_role
```

角色参数省略时读取 `PGEVENTBUS_DB_OWNER`。名称虽然在代码中称为 owner，实际只替换 SQL 的授权对象 `app_role`，不创建角色、不转移对象所有权。函数和对象归执行 DDL 的账号所有。

CLI 使用 `go:embed` 内嵌 `cli/sql/init.sql` 模板，只渲染 schema/owner 标记，通过同一连接的 simple protocol 完整执行事务脚本，不按分号拆分。失败时尝试同连接 ROLLBACK，然后关闭连接。删除原 classpath、`PGEVENTBUS_SQL_DIR` 和向上查找 docs 的回退；程序复制到仓库外仍自带初始化 SQL。该模板带有标记，不可直接手工执行。

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
| `event recover <consumerName> <isoInstant>` | 按时间重设位点 |
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

## 维护

`maintenance run` 固定调用 `ensure_future_partitions(6)` 和 `drop_partition_older_than(3)`。保留阈值为当前月份月初减去 3 个月，删除函数意图删除早于该月份的整个分区，独立于消费者进度。

`cleanupConsumed` 在 Java / HTTP 客户端及网关提供，CLI 没有单独命令。当前 SQL 依据所有已注册消费者的最小位点选择 `id < min(last_event_id)` 的批次，默认批大小 10000。

仓库没有维护或重试定时任务，需由调用方调度。清理的跨分区 `ctid` 匹配存在实现边界，详见[SQL 文档](PG-EventBus.md#当前实现限制)。
