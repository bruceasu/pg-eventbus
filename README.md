# pg-eventbus

基于 PostgreSQL 的事件总线：将 JSON 事件写入按月分区的日志，通过消费者位点拉取事件，并提供租约锁、重试队列、死信和数据维护函数。

本文及相关指南描述当前工作区实现。Java 配置前缀为 `bus`，默认 schema 为 `bus`。

## 模块与接入方式

| 模块 | 职责 / 接入方式 |
|---|---|
| `client/java/db` | 独立 Java 直连客户端，包含自身 JDBC、服务及 DTO |
| `client/java/gateway` | 独立 Java Gateway 客户端，通过 JDK HTTP 调用网关 |
| `cli` | 独立 Go 维护程序，自带初始化 SQL，通过 pgx 执行 SQL |
| `gateway` | 独立 Go HTTP 服务，使用 pgx 连接池调用数据库函数 |
| `client/go/db` / `client/python/db` | 通过外部 `psql` 命令访问数据库 |
| `client/go/gateway` / `client/python/gateway` | 调用网关的 Go / Python 客户端 |
| `client/javascript/db` / `client/javascript/gateway` | JS ESM + TypeScript 声明；注入 PostgreSQL query / 调用 HTTP，ID为bigint |

客户端统一放在 `client/<语言>/db` 和 `client/<语言>/gateway`。JS/TS共用javascript目录，独立ESM包、无需预构建；Node.js >=22.12，详见[JS/TS客户端](client/javascript/README.md)。CLI、Gateway 和客户端互不依赖；原 core、jdbc、contract 已合并到 Java 直连客户端，不再作为独立模块。CLI 与 Gateway 均使用 Go/pgx，不使用 ORM。

## 构建

Java 项目要求 **JDK 25**，使用 Spring Boot 3.5.4。根 POM 仅聚合构建，各项目不继承根 POM。在仓库根目录运行：

```powershell
.\mvnw.cmd clean install
```

也可使用已安装的 Maven 执行 `mvn clean install`。根 POM 仅聚合两个 Java 客户端。Go 客户端要求 Go 1.22，CLI 和 Gateway 要求 Go 1.25，Python 包要求 Python 3.10+；它们不属于 Maven reactor。

Gateway 与 CLI 使用 Go 独立构建，无需 JVM 或项目内模块：

```powershell
cd gateway
$env:GOTOOLCHAIN = 'local'
$env:CGO_ENABLED = '0'
go build -o bin/pg-eventbus-gateway.exe .
cd ..
cd cli
$env:GOTOOLCHAIN = 'local'
$env:CGO_ENABLED = '0'
go generate ./sql
go run ./internal/cmd/gensql -source ../docs/sql -output sql/init.sql -check
go build -o bin/pg-eventbus-cli.exe .
```

## 配置

Go CLI 使用运行目录的 `pg-eventbus.toml`，也可通过 `--config` 选择文件；示例见 [pg-eventbus.example.toml](cli/pg-eventbus.example.toml)。参数采用 `--database-url`、`--schema` 等短名称，环境变量统一为 `PGEVENTBUS_*`，完整映射见 [CLI 说明](cli/README.md)。

Gateway 只读取环境变量，默认监听 `:8080`。推荐设置 `PGEVENTBUS_DATABASE_URL`、`PGEVENTBUS_DATABASE_USER`、`PGEVENTBUS_DATABASE_PASSWORD`、`PGEVENTBUS_SCHEMA`；旧 Spring 环境变量仍作为低优先级别名。完整映射、请求期限与迁移说明见 [gateway/README](gateway/README.md)。不再读取 Spring 配置文件。

下面的 `application.yml` 仅适用于由宿主装配的 Java 直连客户端：

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

CLI 与 Gateway 默认批大小 100、锁 TTL 30 秒；Java 直连库由宿主提供配置。Java 环境变量使用 `BUS_*`、`SPRING_DATASOURCE_*`；Go CLI 使用 `PGEVENTBUS_*`，不兼容 Java 格式。Gateway 原生变量也为 `PGEVENTBUS_*`，另保留旧环境变量别名，但不会读取 CLI 的 TOML。

Go/Python 直连客户端使用 `PGEVENTBUS_SCHEMA` 和外部命令的连接配置；HTTP 客户端的 schema 由网关决定。详见[配置与 CLI](docs/pg-eventbus-schema-and-cli-addendum.md)。

## 初始化与事件操作

先创建 PostgreSQL 数据库和授权角色。初始化时使用具有建 schema、表、函数及授权权限的数据库账号；`db init app_role` 中的 `app_role` 是授权对象，不会创建角色或数据库。

以下 PowerShell 示例从仓库根目录调用 Go CLI：

```powershell
$cli = '.\cli\bin\pg-eventbus-cli.exe'
& $cli db init app_role
& $cli partition ensure-current
& $cli partition ensure-future
& $cli event send source-a consumer-a price-update '{}'
& $cli event get consumer-a instance-a 100
# 处理成功后，将末条已成功处理事件的 ID 替换到命令中
& $cli event set-cursor consumer-a instance-a 12345
```

`db init` 执行随 CLI 打包的 `init.sql`，安装表、函数并授权；它本身不预建分区。发布函数会确保当前分区存在。`ensure-future` 创建未来 6 个月分区。

SQL 对象引用不带 schema 前缀。[docs/sql](docs/sql/README.md) 是唯一维护来源，按三位编号安装；手工安装在副本中修改 001_bootstrap.sql 的 schema 和 990_grant_access.sql 的角色。CLI 的 `sql/init.sql` 是生成并内嵌的模板，通过 `PGEVENTBUS_SCHEMA`、`--schema` 或 TOML 的 database.schema 渲染。修改源 SQL 后在 cli/ 运行 `go generate ./sql`，不要直接编辑生成物。

`targetSystem` 必须与消费者名称一致才会被该消费者拉取；SQL 中空目标（`NULL`）表示广播。`poll` 不自动提交位点，业务处理完成后才调用 `commit`。Java/网关的拉取会获取租约锁，提交要求同一实例持有未过期锁。提交不会释放锁，租约未到期时再次拉取（即使同一实例）也会拿锁失败。

## HTTP 网关

使用独立环境变量启动 Go 网关（先完成上述构建）：

```powershell
$env:PGEVENTBUS_DATABASE_URL = 'postgresql://localhost:5432/pg_eventbus'
$env:PGEVENTBUS_DATABASE_USER = 'app_role'
$env:PGEVENTBUS_DATABASE_PASSWORD = 'change-me'
.\gateway\bin\pg-eventbus-gateway.exe
```

所有 API 均为 POST：

| 路径 | 请求体 |
|---|---|
| `/api/event-bus/events` | `sourceSystem`、`targetSystem`、`eventType`、`payload` |
| `/api/event-bus/consumers/{consumerName}/poll` | `instanceId`、可选 `limit` |
| `/api/event-bus/consumers/{consumerName}/commit` | `instanceId`、`newLastId` |
| `/api/event-bus/consumers/{consumerName}/recover` | `startTime`（ISO-8601 时间） |
| `/api/event-bus/maintenance/run` | 无 |
| `/api/event-bus/maintenance/ensure-current-partition` | 无 |
| `/api/event-bus/maintenance/ensure-future-partitions` | 无 |
| `/api/event-bus/maintenance/drop-older-than/{keepMonths}` | 无 |
| `/api/event-bus/maintenance/cleanup-consumed` | 无 |

例如：

```powershell
$body = @{
  sourceSystem = 'source-a'
  targetSystem = 'consumer-a'
  eventType = 'price-update'
  payload = @{ price = 123.45 }
} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/event-bus/events' -ContentType 'application/json' -Body $body
```

网关返回 `ApiResponse<T>` 信封，客户端解包并返回 DTO。提交是否成功应检查 `data.committed`，拉取是否获得锁应检查 `data.lockAcquired`。

## 行为与实现边界

- 消费按事件 ID 升序读取；未提交的数据可以再次出现，业务处理需要幂等。实现不提供跨外部业务系统的 exactly-once 保证。
- `recover` 将位点设为指定时间之前及该时刻的最大事件 ID，不是按时间查询事件，也不会还原已清理的数据。
- 平面客户端的 `poll/commit` 不自动运行回调、重试或死信流程。Java core 提供独立服务，调用方负责调度。
- `maintenance` 固定预建未来 6 个月分区并调用保留参数为 3 的旧分区删除函数，不清理重试或死信。
- Go/Python 直连客户端通过外部 psql 执行 SQL，存在锁、权限和维护 SQL 的接入限制，详见[客户端指南](docs/pg-eventbus-client-guide.md)。独立 Go CLI 使用 pgx 并在拉取前获取租约。
- 分区删除和按位点清理存在需要验证的 SQL 边界，详见[实现与 SQL](docs/PG-EventBus.md)。

## 文档

- [文档索引与维护规则](docs/README.md)
- [客户端接口、Java 装配与消费示例](docs/pg-eventbus-client-guide.md)
- [schema 配置、初始化、CLI 与维护](docs/pg-eventbus-schema-and-cli-addendum.md)
- [数据库模型、消费语义与实现限制](docs/PG-EventBus.md)
- [SQL 执行顺序与生成流程](docs/sql/README.md)
- [将来接入 Flyway 的处理方式](docs/flyway-adoption.md)
- [Java HTTP](client/java/gateway/README.md)、[Go 直连](client/go/db/README.md)、[Go HTTP](client/go/gateway/README.md)、[Python 直连](client/python/db/README.md)、[Python HTTP](client/python/gateway/README.md)

`docs/PG-EventBus.mht` 和 `docs/superpowers/plans/` 保留为历史设计资料；实际行为以代码和当前使用文档为准。
