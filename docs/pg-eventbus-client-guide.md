# pg-eventbus-client 接口与使用说明

本文档面向要在其他项目中接入 `pg-eventbus-client` 的开发者，统一说明 Java / Go / Python 三种客户端的接口、返回结构和基础用法。

本页主要覆盖 **直连 PostgreSQL 的客户端**：

- Java: `pg-eventbus-client-java`
- Go: `client-go`
- Python: `client-python`

如果你的运行环境不能直接访问 PostgreSQL，请改用对应的 HTTP 网关客户端：

- Java: `pg-eventbus-client-java-http`
- Go: `client-go-http`
- Python: `client-python-http`

## 1. 适用场景

- 应用进程可以直接访问 PostgreSQL。
- 希望在代码里直接完成发布、拉取、提交、恢复和维护操作。
- 希望使用同一套事件总线能力，但不想额外引入消息队列中间件。

## 2. 能力总览

三种直连客户端都提供同一组核心能力：

- 发布事件
- 拉取事件
- 提交消费位点
- 按时间恢复消费位点
- 执行维护任务
- 清理已消费数据

## 3. 数据库接入设定

如果你要在自己的应用里使用 `pg-eventbus` 的数据库能力，核心不是给客户端单独传一个连接串，而是让应用先具备 PostgreSQL 数据源，再由 Spring Boot 或你自己的配置把相关 Bean 装配出来。

### 3.1 Spring Boot 标准配置

最常见的方式是在 `application.yml` 或环境变量中配置标准数据源：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/pg_eventbus
    username: pg_eventbus
    password: secret
    driver-class-name: org.postgresql.Driver

bus:
  enabled: true
```

对应的环境变量一般写成：

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/pg_eventbus
SPRING_DATASOURCE_USERNAME=pg_eventbus
SPRING_DATASOURCE_PASSWORD=secret
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
MARKET_INTEL_BUS_ENABLED=true
```

只要应用引入了 Spring Boot 的 JDBC 自动配置，`DataSource`、`JdbcTemplate` 和 `NamedParameterJdbcTemplate` 通常会自动创建，`pg-eventbus-jdbc` 里的仓储就能工作。

### 3.2 手动声明 Bean

如果你的项目没有完全依赖 Spring Boot 自动配置，也可以自己显式声明数据库 Bean：

```java
@Configuration
public class PgEventBusDbConfig {

    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/pg_eventbus");
        config.setUsername("pg_eventbus");
        config.setPassword("secret");
        config.setDriverClassName("org.postgresql.Driver");
        return new HikariDataSource(config);
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }
}
```

### 3.3 事件总线开关

仓库里的核心服务和仓储都受 `bus.enabled=true` 控制。也就是说：

- 数据库连接可以存在，但如果总线开关没打开，相关 Bean 不会生效
- 如果你希望在应用中启用 `PgEventBusClient`，请确保这个开关为 `true`

### 3.4 Java 客户端 Bean 装配

在 `pg-eventbus-gateway` 里，`PgEventBusClient` 的 Bean 会在以下条件都满足时创建：

- `bus.enabled=true`
- `PgEventBusPublisherService`
- `PgEventBusConsumerPollService`
- `PgEventBusConsumerRecoveryService`
- `PgEventBusConsumerCommitService`
- `PgEventBusMaintenanceService`

对应的配置类是：

```java
@Configuration
@ConditionalOnProperty(prefix = "bus", name = "enabled", havingValue = "true")
public class PgEventBusClientConfiguration {
    @Bean
    @ConditionalOnBean({
            PgEventBusPublisherService.class,
            PgEventBusConsumerPollService.class,
            PgEventBusConsumerRecoveryService.class,
            PgEventBusConsumerCommitService.class,
            PgEventBusMaintenanceService.class
    })
    public PgEventBusClient pgEventBusClient(...) {
        ...
    }
}
```

所以在你的业务项目里，推荐做法是：

1. 配好 PostgreSQL 数据源
2. 引入 `pg-eventbus-jdbc` 和 `pg-eventbus-core`
3. 打开 `bus.enabled`
4. 通过 Spring 注入 `PgEventBusClient` 或相关 service

### 3.5 依赖提醒

如果你是把这套能力嵌进 Spring Boot 应用，通常还需要：

- `spring-boot-starter-jdbc`
- PostgreSQL JDBC 驱动
- Jackson

如果你只是在 Go / Python 里使用直连客户端，则不需要 Spring 配置，但仍然需要本机可用的 `psql` 或 `PGEVENTBUS_PSQL_COMMAND` 指定的命令。

## 4. 客户端对照表

| 语言 | 模块 | 构造方式 | 典型特点 |
|---|---|---|---|
| Java | `pg-eventbus-client-java` | 通过 `PgEventBusClient` 构造函数注入底层服务 | 适合 Spring / 业务服务内部封装 |
| Go | `client-go` | `pgeventbus.New()` | 通过 `psql` 命令执行 SQL |
| Python | `client-python` | `PgEventBusClient()` | 通过 `psql` 命令执行 SQL |

## 5. 通用返回结构

### 5.1 PublishResultDto

| 字段 | 说明 |
|---|---|
| `eventId` | 新发布事件的 ID |

### 5.2 PollResultDto

| 字段 | 说明 |
|---|---|
| `consumer` | 当前消费者状态 |
| `events` | 本次拉取到的事件列表 |
| `batchSize` | 拉取批次大小 |
| `lockAcquired` | 是否成功拿到锁 |
| `polledAt` | 拉取时间 |

### 5.3 ConsumerDto

| 字段 | 说明 |
|---|---|
| `consumerName` | 消费者名称 |
| `instanceId` | 实例 ID |
| `locked` | 当前是否持锁 |
| `lockOwner` | 锁持有者 |
| `lockAcquiredAt` | 锁获取时间 |
| `lockExpiresAt` | 锁过期时间 |
| `lastCommittedEventId` | 已提交的最后事件 ID |
| `currentOffset` | 当前消费位点 |
| `batchSize` | 当前批大小 |

### 5.4 EventDto

| 字段 | 说明 |
|---|---|
| `id` | 事件 ID |
| `sourceSystem` | 来源系统 |
| `targetSystem` | 目标系统 |
| `eventType` | 事件类型 |
| `payload` | JSON 负载 |
| `createdAt` | 创建时间 |

### 5.5 CommitResultDto

| 字段 | 说明 |
|---|---|
| `committed` | 是否提交成功 |
| `newLastCommittedEventId` | 新的已提交事件 ID |

### 5.6 RecoverResultDto

| 字段 | 说明 |
|---|---|
| `initializedOffset` | 初始化得到的消费位点 |
| `consumer` | 初始化后的消费者状态 |

### 5.7 MaintenanceResultDto

| 字段 | 说明 |
|---|---|
| `executed` | 是否已执行维护 |
| `actions` | 已执行的维护动作列表 |
| `completedAt` | 完成时间 |

## 6. Java 接口说明

### 6.1 构造方式

`pg-eventbus-client-java` 是一个薄封装，直接包住底层的发布、拉取、恢复和维护服务。通常在应用内部由依赖注入框架创建。

```java
PgEventBusClient client = new PgEventBusClient(
        publisherService,
        pollService,
        recoveryService,
        commitService,
        maintenanceService
);
```

### 6.2 方法列表

| 方法 | 参数 | 返回值 | 说明 |
|---|---|---|---|
| `publish` | `sourceSystem`, `targetSystem`, `eventType`, `JsonNode payload` | `PublishResultDto` | 发布事件 |
| `poll` | `consumerName`, `instanceId`, `Integer requestedLimit` | `PollResultDto` | 拉取事件 |
| `commit` | `consumerName`, `instanceId`, `long newLastId` | `CommitResultDto` | 提交消费位点 |
| `recover` | `consumerName`, `Instant startTime` | `RecoverResultDto` | 按时间恢复 |
| `ensureCurrentPartition` | 无 | `MaintenanceResultDto` | 确保当前分区存在 |
| `ensureFuturePartitions` | 无 | `MaintenanceResultDto` | 确保未来分区存在 |
| `dropOldPartitions` | 无 / `int keepMonths` | `MaintenanceResultDto` | 清理旧分区 |
| `runMaintenance` | 无 | `MaintenanceResultDto` | 执行统一维护流程 |
| `cleanupConsumed` | 无 | `int` | 清理已消费数据，返回清理数量 |

### 6.3 Java 示例

```java
JsonNode payload = objectMapper.createObjectNode().put("price", 123.45);

PublishResultDto published = client.publish("source", "target", "price-update", payload);
PollResultDto polled = client.poll("consumer-a", "instance-a", 100);
CommitResultDto committed = client.commit("consumer-a", "instance-a", published.eventId());
RecoverResultDto recovered = client.recover("consumer-a", Instant.now());
MaintenanceResultDto maintenance = client.runMaintenance();
int cleaned = client.cleanupConsumed();
```

## 7. Go 接口说明

### 7.1 安装与导入

```go
import (
    "context"
    "encoding/json"
    "time"

    pgeventbus "github.com/bruceasu/pg-eventbus/client-go"
)
```

### 7.2 命令解析顺序

Go 直连客户端会按以下顺序寻找 PostgreSQL 命令：

1. `PGEVENTBUS_PSQL_COMMAND`
2. 系统 `PATH` 中的 `psql`
3. `PGEVENTBUS_PGSQL_CLIENT_DIR`
4. 仓库或程序目录下的 `tools/pgsql-client`

### 7.3 方法列表

| 方法 | 参数 | 返回值 | 说明 |
|---|---|---|---|
| `Publish` | `ctx`, `sourceSystem`, `targetSystem`, `eventType`, `json.RawMessage payload` | `(PublishResultDto, error)` | 发布事件 |
| `Poll` | `ctx`, `consumerName`, `instanceID`, `*int limit` | `(PollResultDto, error)` | 拉取事件，`limit == nil` 时默认 100 |
| `Commit` | `ctx`, `consumerName`, `instanceID`, `newLastID` | `(CommitResultDto, error)` | 提交消费位点 |
| `Recover` | `ctx`, `consumerName`, `time.Time startTime` | `(RecoverResultDto, error)` | 按时间恢复 |
| `RunMaintenance` | `ctx` | `(MaintenanceResultDto, error)` | 执行统一维护流程 |
| `EnsureCurrentPartition` | `ctx` | `(MaintenanceResultDto, error)` | 确保当前分区存在 |
| `EnsureFuturePartitions` | `ctx` | `(MaintenanceResultDto, error)` | 确保未来分区存在 |
| `DropOldPartitions` | `ctx`, `keepMonths` | `(MaintenanceResultDto, error)` | 清理旧分区 |
| `CleanupConsumed` | `ctx` | `(int, error)` | 清理已消费数据，返回清理数量 |

### 7.4 Go 示例

```go
client := pgeventbus.New()
ctx := context.Background()

payload := json.RawMessage(`{"price":123.45}`)
published, err := client.Publish(ctx, "source", "target", "price-update", payload)
if err != nil {
    return err
}

polled, err := client.Poll(ctx, "consumer-a", "instance-a", nil)
if err != nil {
    return err
}

committed, err := client.Commit(ctx, "consumer-a", "instance-a", published.EventID)
if err != nil {
    return err
}

recovered, err := client.Recover(ctx, "consumer-a", time.Now().UTC())
if err != nil {
    return err
}

maintenance, err := client.RunMaintenance(ctx)
if err != nil {
    return err
}

cleaned, err := client.CleanupConsumed(ctx)
```

### 7.5 错误处理

Go 客户端使用标准 `error` 返回值；命令执行失败时会带上底层 `psql` 的输出，方便排查 SQL 或环境问题。

## 8. Python 接口说明

### 8.1 安装与导入

```python
from pg_eventbus import PgEventBusClient
```

### 8.2 命令解析顺序

Python 直连客户端会按以下顺序寻找 PostgreSQL 命令：

1. `PGEVENTBUS_PSQL_COMMAND`
2. 系统 `PATH` 中的 `psql`
3. `PGEVENTBUS_PGSQL_CLIENT_DIR`
4. 仓库或程序目录下的 `tools/pgsql-client`

### 8.3 方法列表

| 方法 | 参数 | 返回值 | 说明 |
|---|---|---|---|
| `publish` | `source_system`, `target_system`, `event_type`, `payload` | `PublishResultDto` | 发布事件 |
| `poll` | `consumer_name`, `instance_id`, `limit=None` | `PollResultDto` | 拉取事件，`limit` 为空时默认 100 |
| `commit` | `consumer_name`, `instance_id`, `new_last_id` | `CommitResultDto` | 提交消费位点 |
| `recover` | `consumer_name`, `start_time=None` | `RecoverResultDto` | 按时间恢复；为空时使用当前 UTC 时间 |
| `run_maintenance` | 无 | `MaintenanceResultDto` | 执行统一维护流程 |
| `ensure_current_partition` | 无 | `MaintenanceResultDto` | 确保当前分区存在 |
| `ensure_future_partitions` | 无 | `MaintenanceResultDto` | 确保未来分区存在 |
| `drop_old_partitions` | `keep_months` | `MaintenanceResultDto` | 清理旧分区 |
| `cleanup_consumed` | 无 | `int` | 清理已消费数据，返回清理数量 |

### 8.4 Python 示例

```python
from datetime import datetime, timezone
from pg_eventbus import PgEventBusClient

client = PgEventBusClient()

published = client.publish("source", "target", "price-update", {"price": 123.45})
polled = client.poll("consumer-a", "instance-a", 100)
committed = client.commit("consumer-a", "instance-a", published.event_id)
recovered = client.recover("consumer-a", datetime.now(timezone.utc))
maintenance = client.run_maintenance()
cleaned = client.cleanup_consumed()
```

### 8.5 错误处理

Python 客户端抛出 `PgEventBusError`，错误消息会包含错误码、错误信息，以及可选的 details。

## 9. 使用建议

- 事件负载建议使用标准 JSON 对象，避免传入语言私有结构。
- `recover` 的时间建议使用 UTC 时间。
- `poll` 的 `instanceId` 建议使用稳定且可区分的实例标识。
- 如果你的应用无法直接访问 PostgreSQL，请改用 HTTP 网关客户端，不要在代码里硬编码命令行回退路径。

## 10. 典型接入流程

1. 确认应用是否能直接访问 PostgreSQL。
2. 选择对应语言的直连客户端或 HTTP 客户端。
3. 配置 `psql` 命令或网关地址。
4. 先调用 `publish` 发送一条测试事件。
5. 再调用 `poll` / `commit` 验证消费链路。
6. 最后把 `maintenance` 相关操作接入运维任务。