# 配置、数据库初始化与 CLI

本页依据 `PgEventBusProperties`、JDBC 配置、CLI runner 和初始化服务编写。项目概览见[README](../README.md)。

## Java / CLI / 网关配置

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

CLI 与 Gateway 的 `application.properties` 提供批大小 100、锁 TTL 30 秒；Java 直连库由宿主提供配置，`bus.enabled` 和 `bus.max-retries` 仅用于该客户端。schema 的空白值回退到 `bus`。JDBC 调用会引用并转义标识符；CLI 初始化额外要求 schema 和授权角色符合 `[A-Za-z_][A-Za-z0-9_]*`。建议使用小写名称，因为初始化 SQL 使用未加双引号的标识符。

Java 配置不读取 `PGEVENTBUS_SCHEMA`。自定义 schema 使用 `bus.schema: analytics` 或 `BUS_SCHEMA=analytics`，初始化与运行时必须保持一致。

CLI 和 Gateway 各自包含数据源配置和直接 SQL 服务，根据 `spring.datasource.url` 创建数据库 Bean；它们不依赖客户端或其它项目模块，也不使用 ORM。

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

手动安装时，修改 `cli/src/main/resources/init.sql` 开头的两条语句来选择目标 schema，例如：

```sql
CREATE SCHEMA IF NOT EXISTS analytics;
SET LOCAL search_path = analytics, pg_temp;
```

`SET LOCAL` 位于脚本的 `BEGIN/COMMIT` 内，仅对安装事务生效。表、函数及函数体中的对象引用均不带 schema 前缀；函数通过 `SET search_path FROM CURRENT` 保存安装时的搜索路径，因此调用方无需设置相同路径。`pg_catalog` 隐式优先查找，`pg_temp` 显式放在末尾。安装 schema 仅应允许可信的 DDL 账号创建对象。

手动执行前，还需将授权脚本中的 `app_role` 改为已存在的应用角色；授权和分区检查自动使用选定 schema。拆分安装时，先修改 `docs/sql/bootstrap.sql` 中的 `CREATE SCHEMA` 和 `SET search_path`，再在同一连接内按依赖顺序执行其余脚本。CLI 安装继续使用 `bus.schema` / `BUS_SCHEMA` 选择 schema，运行时配置也应使用相同名称。

先创建数据库和授权角色，再用具有 DDL 和授权权限的账号配置数据源并执行：

```powershell
$cliJar = 'cli/target/pg-eventbus-cli-0.0.1-SNAPSHOT.jar'
java -jar $cliJar db init app_role
```

角色参数省略时读取 `PGEVENTBUS_DB_OWNER`。名称虽然在代码中称为 owner，实际只替换 SQL 的授权对象 `app_role`，不创建角色、不转移对象所有权。函数和对象归执行 DDL 的账号所有。

初始化服务优先读取 classpath 根目录的 `init.sql`，即 `cli/src/main/resources/init.sql`。该合并脚本用 `BEGIN/COMMIT` 包围表、函数和授权安装，替换其中完整单词 `bus` 和 `app_role` 后一次交给 JDBC 执行。

只有 classpath 缺少脚本时才使用文件系统回退：

1. 有效的 `PGEVENTBUS_SQL_DIR` 目录中的 `init.sql`。
2. 从工作目录逐级向上找到的 `docs/sql/init.sql`。

当前仓库 `docs/sql/` 只有拆分脚本，没有 `init.sql`。CLI 只打包自身的 `src/main/resources/init.sql`，可在脱离仓库后执行初始化；设置 `PGEVENTBUS_SQL_DIR` 不会覆盖已存在的 classpath 脚本。

`db init` **不预建分区**。需要立即预建时：

```powershell
java -jar $cliJar partition ensure-current
java -jar $cliJar partition ensure-future
```

发布函数也会确保当前月份分区存在。合并脚本使用 `IF NOT EXISTS` 和 `CREATE OR REPLACE FUNCTION`，但不是版本化 schema 迁移工具。

## CLI 命令

当前仓库没有安装名为 `pg-eventbus-cli` 的 shell 启动器，使用[根 README 的打包命令](../README.md#构建)生成可执行 JAR 后，通过 `java -jar` 调用。

| JAR 后的参数 | 行为 |
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
java -jar $cliJar event send source-a consumer-a price-update '{}'
java -jar $cliJar event get consumer-a instance-a 100
# 替换为已成功处理的末条事件 ID，且须在锁到期前提交
java -jar $cliJar event set-cursor consumer-a instance-a 12345
# 此命令会重设位点，应在需要恢复时执行
java -jar $cliJar event recover consumer-a 2026-10-01T00:00:00Z
```

JSON 参数须完整作为一个 shell 参数传入；不同 PowerShell 版本对原生程序引号的传递有差异，复杂负载可通过网关发送。事件/维护命令打印 JSON，但 Spring 启动日志可能混在 stdout；`db init` 打印完成文本。缺少参数、未知命令或对应 Bean 不存在时通常只打印 usage，不能仅凭进程退出码判断操作成功。

## 维护

`maintenance run` 固定调用 `ensure_future_partitions(6)` 和 `drop_partition_older_than(3)`。保留阈值为当前月份月初减去 3 个月，删除函数意图删除早于该月份的整个分区，独立于消费者进度。

`cleanupConsumed` 在 Java / HTTP 客户端及网关提供，CLI 没有单独命令。当前 SQL 依据所有已注册消费者的最小位点选择 `id < min(last_event_id)` 的批次，默认批大小 10000。

仓库没有维护或重试定时任务，需由调用方调度。清理的跨分区 `ctid` 匹配存在实现边界，详见[SQL 文档](PG-EventBus.md#当前实现限制)。
