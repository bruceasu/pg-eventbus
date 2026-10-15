# pg-eventbus

基于 PostgreSQL 的事件总线：将 JSON 事件写入按月分区的日志，通过消费者位点拉取事件，并提供租约锁、重试队列、死信和数据维护函数。

本文及相关指南描述当前工作区实现。Java 配置前缀为 `bus`，默认 schema 为 `bus`。

## 模块与接入方式

| 模块 | 职责 / 接入方式 |
|---|---|
| `client/java/db` | 独立 Java 直连客户端，包含自身 JDBC、服务及 DTO |
| `client/java/gateway` | 独立 Java Gateway 客户端，通过 JDK HTTP 调用网关 |
| `cli` | 独立维护程序，自带初始化 SQL，通过 JDBC 执行 SQL |
| `gateway` | 独立 HTTP 服务，直接通过 JDBC 调用数据库函数 |
| `client/go/db` / `client/python/db` | 通过外部 `psql` 命令访问数据库 |
| `client/go/gateway` / `client/python/gateway` | 调用网关的 Go / Python 客户端 |

客户端统一放在 `client/<语言>/db` 和 `client/<语言>/gateway`。CLI、Gateway 和客户端互不依赖；原 core、jdbc、contract 已合并到 Java 直连客户端，不再作为独立模块。CLI 与 Gateway 各自维护 JDBC 调用，不使用 ORM。

## 构建

Java 项目要求 **JDK 25**，使用 Spring Boot 3.5.4。根 POM 仅聚合构建，各项目不继承根 POM。在仓库根目录运行：

```powershell
.\mvnw.cmd clean install
```

也可使用已安装的 Maven 执行 `mvn clean install`。Go 模块声明 Go 1.22，Python 包要求 Python 3.10+；它们不属于 Maven reactor。

CLI 与 Gateway 可分别独立构建，`package` 自动生成包含依赖的可执行 JAR，无需先构建或安装任何项目内模块：

```powershell
.\mvnw.cmd -f cli/pom.xml clean package
.\mvnw.cmd -f gateway/pom.xml clean package
```

## 配置

在运行目录准备 `application.yml`，或使用对应的 Spring 环境变量：

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

CLI 与 Gateway 自带批大小 100、锁 TTL 30 秒的配置；Java 直连库仍由宿主应用提供配置。环境变量为 `BUS_SCHEMA`、`BUS_BATCH_SIZE`、`BUS_LOCK_TTL_SECONDS`，数据源使用 `SPRING_DATASOURCE_*`。

Go/Python 直连客户端使用 `PGEVENTBUS_SCHEMA` 和外部命令的连接配置；HTTP 客户端的 schema 由网关决定。详见[配置与 CLI](docs/pg-eventbus-schema-and-cli-addendum.md)。

## 初始化与事件操作

先创建 PostgreSQL 数据库和授权角色。初始化时使用具有建 schema、表、函数及授权权限的数据库账号；`db init app_role` 中的 `app_role` 是授权对象，不会创建角色或数据库。

以下 PowerShell 示例使用显式打包后的 CLI JAR：

```powershell
$cliJar = 'cli/target/pg-eventbus-cli-0.0.1-SNAPSHOT.jar'
java -jar $cliJar db init app_role
java -jar $cliJar partition ensure-current
java -jar $cliJar partition ensure-future
java -jar $cliJar event send source-a consumer-a price-update '{}'
java -jar $cliJar event get consumer-a instance-a 100
# 处理成功后，将末条已成功处理事件的 ID 替换到命令中
java -jar $cliJar event set-cursor consumer-a instance-a 12345
```

`db init` 执行随 CLI 打包的 `init.sql`，安装表、函数并授权；它本身不预建分区。发布函数会确保当前分区存在。`ensure-future` 创建未来 6 个月分区。

SQL 对象引用不带 schema 前缀。手动安装时，在 `init.sql` 开头的 `CREATE SCHEMA` 和 `SET LOCAL search_path` 两处指定目标 schema，并替换授权角色 `app_role`；CLI 使用 `BUS_SCHEMA` 或 `bus.schema` 自动选择。具体步骤见[初始化说明](docs/pg-eventbus-schema-and-cli-addendum.md#数据库初始化)。

`targetSystem` 必须与消费者名称一致才会被该消费者拉取；SQL 中空目标（`NULL`）表示广播。`poll` 不自动提交位点，业务处理完成后才调用 `commit`。Java/网关的拉取会获取租约锁，提交要求同一实例持有未过期锁。提交不会释放锁，租约未到期时再次拉取（即使同一实例）也会拿锁失败。

## HTTP 网关

使用相同的数据源和 `bus` 配置启动：

```powershell
java -jar gateway/target/pg-eventbus-gateway-0.0.1-SNAPSHOT.jar
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
- 当前 Go/Python 客户端源码含非法 DTO 标识符；直连版还有锁、权限和维护 SQL 的接入限制。
- 分区删除和按位点清理存在需要验证的 SQL 边界，详见[实现与 SQL](docs/PG-EventBus.md)。

## 文档

- [客户端接口、Java 装配与消费示例](docs/pg-eventbus-client-guide.md)
- [schema 配置、初始化、CLI 与维护](docs/pg-eventbus-schema-and-cli-addendum.md)
- [数据库模型、消费语义与实现限制](docs/PG-EventBus.md)
- [Java HTTP](client/java/gateway/README.md)、[Go 直连](client/go/db/README.md)、[Go HTTP](client/go/gateway/README.md)、[Python 直连](client/python/db/README.md)、[Python HTTP](client/python/gateway/README.md)

`docs/PG-EventBus.mht` 和 `docs/superpowers/plans/` 保留为历史设计资料；实际行为以代码和当前使用文档为准。
