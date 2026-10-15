# 客户端接口与使用说明

本页覆盖 Java、Go、Python 的直连和 HTTP 两类客户端。配置与初始化见[CLI 文档](pg-eventbus-schema-and-cli-addendum.md)，底层行为见[实现与 SQL](PG-EventBus.md)。

## 接入选择与当前状态

| 语言 | 直连 PostgreSQL | HTTP 网关 | 构造方式 |
|---|---|---|---|
| Java | `pg-eventbus-client-java`，JDBC/core 服务 | `pg-eventbus-client-java-http` | 直连注入五个服务；HTTP 传入网关根地址 |
| Go | `client/go/db`，外部 psql 命令 | `client/go/gateway` | `New()` / `New(baseURL)` |
| Python | `client/python/db`，外部 psql 命令 | `client/python/gateway` | `PgEventBusClient()` / `PgEventBusClient(base_url)` |

所有客户端按语言放在 `client/<语言>/db` 与 `client/<语言>/gateway`，分别独立构建。独立的 Go CLI 使用 pgx；这里的 Go 直连客户端仍使用外部 psql，两者的连接方式与配置不能混用。

直连 Go/Python 还存在额外差异：poll 不获取锁，查询 registry 要求表读权限，维护 SQL 有结构问题。需要先获取租约再拉取时可通过 Go 网关。Python 两个分发包均暴露 `pg_eventbus` 导入名，应安装在独立虚拟环境。

## Java 依赖与装配

Java 构建目标为 JDK 25，Maven 坐标版本为 `0.0.1-SNAPSHOT`。先在仓库根目录执行 `mvn clean install`（Windows 可用 `.\mvnw.cmd`），再在业务项目中引入：

```xml
<dependency>
    <groupId>me.asu</groupId>
    <artifactId>pg-eventbus-client-java</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

使用 HTTP 时将 artifactId 改为 `pg-eventbus-client-java-http`。Java 直连 DTO 包为 `me.asu.pg_eventbus.contract`，HTTP DTO 包为 `me.asu.pg_eventbus.client.http.model`；两种客户端可同时引入，类型不冲突。每个客户端包含自身实现及 DTO，无需引入其它项目模块。

直连门面没有 Spring 组件注解，也没有独立 starter 自动配置。业务应用需：

1. 配置 `spring.datasource.*` 和 `bus.enabled=true`，并显式设置批大小、锁 TTL 和最大重试数。
2. 扫描或导入 JDBC/core 的配置、仓储及所需服务，注册 `PgEventBusProperties` 配置绑定。
3. 提供 `ObjectMapper` 和 JDBC Bean，再将五个服务装配为客户端。
4. 使用回调消费/重试服务时另行注册名为 `busTransactionManager` 的事务管理器。

业务应用可扫描 `me.asu.pg_eventbus.jdbc` 和 `me.asu.pg_eventbus.core`。这些包已收进直连客户端 JAR；CLI 和 Gateway 各自拥有实现，不参与客户端装配。

下列配置片段用于已经装配 JDBC/core 服务的应用：

```java
import me.asu.pg_eventbus.client.PgEventBusClient;
import me.asu.pg_eventbus.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventBusClientConfig {
    @Bean
    public PgEventBusClient pgEventBusClient(
            PgEventBusPublisherService publisher,
            PgEventBusConsumerPollService poll,
            PgEventBusConsumerRecoveryService recovery,
            PgEventBusConsumerCommitService commit,
            PgEventBusMaintenanceService maintenance) {
        return new PgEventBusClient(publisher, poll, recovery, commit, maintenance);
    }
}
```

回调服务的事务管理器可用连接事件数据库的 DataSource 定义：

```java
@Bean(name = "busTransactionManager")
public org.springframework.jdbc.datasource.DataSourceTransactionManager busTransactionManager(
        javax.sql.DataSource dataSource) {
    return new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource);
}
```

## 方法对照

| 操作 | Java 直连 / HTTP | Go 直连 / HTTP | Python 直连 / HTTP |
|---|---|---|---|
| 发布 | `publish(source, target, type, JsonNode)` | `Publish(ctx, source, target, type, json.RawMessage)` | `publish(source, target, type, payload)` |
| 拉取 | `poll(consumer, instance, Integer limit)` | `Poll(ctx, consumer, instance, *int limit)` | `poll(consumer, instance, limit=None)` |
| 提交 | `commit(consumer, instance, long id)` | `Commit(ctx, consumer, instance, int64 id)` | `commit(consumer, instance, id)` |
| 恢复 | `recover(consumer, Instant)` | `Recover(ctx, consumer, time.Time)` | `recover(consumer, start_time=None)` |
| 当前分区 | `ensureCurrentPartition()` | `EnsureCurrentPartition(ctx)` | `ensure_current_partition()` |
| 未来分区 | `ensureFuturePartitions()` | `EnsureFuturePartitions(ctx)` | `ensure_future_partitions()` |
| 删除旧分区 | `dropOldPartitions(int keepMonths)` | `DropOldPartitions(ctx, keepMonths)` | `drop_old_partitions(keep_months)` |
| 综合维护 | `runMaintenance()` | `RunMaintenance(ctx)` | `run_maintenance()` |
| 按消费进度清理 | `cleanupConsumed()` | `CleanupConsumed(ctx)` | `cleanup_consumed()` |

Java 直连另有无参数 `dropOldPartitions()`，保留参数为 3；Java HTTP 必须传参。Go 方法返回 `(结果, error)`，清理返回 `(int, error)`；Java/Python 返回 DTO，清理返回整数。

Java/网关：limit 为空或非正数时使用 `bus.batch-size`。Go 直连：仅 nil 使用 100。Python 直连：None 或 0 使用 100。HTTP Go/Python 未提供 limit 时由网关决定。

Go/Java 恢复必须提供时间；Python 两版默认使用当前 UTC 时间。恢复会直接重设位点，初次接入若需要历史事件，应指定足够早的时间，不能先恢复到当前时间再期待拉取旧事件。

## 返回结构

HTTP JSON 字段使用 camelCase。Java record 访问器同名；Go DTO 字段使用 PascalCase（例如 `EventID`），Python 使用 snake_case（例如 `event_id`）。Go 的 ID、位点字段为 int64。

| 结果 | 字段 |
|---|---|
| PublishResultDto | `eventId` |
| EventDto | `id`、`sourceSystem`、`targetSystem`、`eventType`、`payload`、`createdAt` |
| PollResultDto | `consumer`、`events`、`batchSize`、`lockAcquired`、`polledAt` |
| CommitResultDto | `committed`、`newLastCommittedEventId` |
| RecoverResultDto | `initializedOffset`、`consumer` |
| MaintenanceResultDto | `executed`、`actions`、`completedAt` |

ConsumerDto 包含 `consumerName`、`instanceId`、`locked`、`lockOwner`、`lockAcquiredAt`、`lockExpiresAt`、`lastCommittedEventId`、`currentOffset`、`batchSize`。

Java 门面及 Gateway 的 poll 构造的 ConsumerDto 锁时间为 null、位点为 0；recover 返回初始化位点。Go CLI 也使用这种占位状态。它们不是 registry 的完整状态查询。提交结果中的 ID 是请求值；只有 `committed=true` 才表示 SQL 更新成功，实际位点由数据库 GREATEST 规则决定。

HTTP 网关正常响应信封为：

```json
{
  "success": true,
  "code": "OK",
  "message": "event published",
  "requestId": null,
  "timestamp": "2026-10-15T00:00:00Z",
  "data": {"eventId": 12345},
  "error": null
}
```

客户端会解包 `data`。顶层 success 表示入口正常完成，不表示获得锁或提交成功。Go 网关的业务入口错误也使用失败信封；参数 400、体积超限 413、媒体类型 415、数据库失败 500、数据库请求期限超时 504。未知路由/方法使用标准 404/405；不保证代理和 HTTP 传输层错误也是 JSON。

## HTTP 路由与请求

所有路由使用 POST，基础路径为 `/api/event-bus`。Go Gateway 要求启动时有有效数据库 URL，不使用 `bus.enabled`。配置只读环境变量，详见 [Gateway 迁移说明](../gateway/README.md)。有请求体的路由要求 application/json（或 application/*+json），最大 1 MiB；未知属性忽略，必填字符串不能为空白，payload 不能缺失/null。

| 相对路径 | JSON 请求体 |
|---|---|
| `/events` | `sourceSystem`、`targetSystem`、`eventType`（均非空白），`payload`（非空） |
| `/consumers/{consumerName}/poll` | `instanceId`（非空白）、可选 `limit` |
| `/consumers/{consumerName}/commit` | `instanceId`（非空白）、`newLastId`（必填） |
| `/consumers/{consumerName}/recover` | `startTime`（必填时间） |
| `/maintenance/run` | 无 |
| `/maintenance/ensure-current-partition` | 无 |
| `/maintenance/ensure-future-partitions` | 无 |
| `/maintenance/drop-older-than/{keepMonths}` | 无 |
| `/maintenance/cleanup-consumed` | 无；响应 data 为删除数量 |

请求定义见 [Gateway API 源码](../gateway/internal/api/api.go)，结果模型见 [Gateway DTO](../gateway/internal/store/model.go)。HTTP 输出时间使用带时区 RFC3339Nano 字符串；recover 接受带时区字符串，也兼容 Java 客户端默认的数值 epoch seconds，数值长度/范围见 [Gateway README](../gateway/README.md)。Go CLI 输出使用数值型 epoch seconds，不能直接当作 HTTP 客户端 DTO JSON 使用。limit 缺失/null/非正数时使用配置批大小；keepMonths 接受有符号 int32；newLastId 为必填 int64。

## Java 发布与消费示例

下列方法接收已装配的直连客户端；HTTP 客户端也提供相同的发布、拉取和提交方法。

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import me.asu.pg_eventbus.client.PgEventBusClient;
import me.asu.pg_eventbus.contract.EventDto;
import me.asu.pg_eventbus.contract.PollResultDto;

public void consumeOnce(PgEventBusClient client) {
    var payload = new ObjectMapper().createObjectNode().put("price", 123.45);
    client.publish("source-a", "consumer-a", "price-update", payload);

    PollResultDto result = client.poll("consumer-a", "instance-a", 100);
    if (!result.lockAcquired()) {
        return;
    }
    long lastHandledId = 0;
    for (EventDto event : result.events()) {
        handleIdempotently(event); // 业务方法；异常时停止，保留未处理事件
        lastHandledId = event.id();
    }
    if (lastHandledId > 0
            && !client.commit("consumer-a", "instance-a", lastHandledId).committed()) {
        throw new IllegalStateException("Consumer lease expired or commit rejected");
    }
}
```

`handleIdempotently` 由业务实现。示例处理全部成功后才提交，处理中失败时整批可能重放。不要将刚发布的 eventId 直接当作任意消费者的提交位点。

平面 poll/commit 不释放租约，下一轮 poll 需等租约到期，或 Java 调用 recovery service 的 `releaseLock`。长耗时处理需自行续租；网关没有公开续租/释放端点。

若希望 Java 服务管理回调、成功前缀提交及释放锁，调用 `PgEventBusConsumerService.pollAndHandle`；重试另行调用 `PgEventBusRetryService.processDueRetries`，并配置上述事务管理器。失败事件可能同时留在正常流和重试队列，处理必须幂等。

## Java HTTP 示例

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import me.asu.pg_eventbus.client.http.PgEventBusHttpClient;
import me.asu.pg_eventbus.client.http.model.PublishResultDto;

var client = new PgEventBusHttpClient("http://localhost:8080");
PublishResultDto published = client.publish(
        "source-a", "consumer-a", "price-update",
        new ObjectMapper().createObjectNode().put("price", 123.45));
```

地址传网关根 URL，不附加 `/api/event-bus`。构造重载允许传入 HttpClient 和 ObjectMapper；默认构造未设置显式请求超时。HTTP/信封失败、I/O 失败抛出 IllegalStateException，中断时恢复线程中断状态。

## Go 接入

两个 module 路径分别为：

```text
github.com/bruceasu/pg-eventbus/client/go/db
github.com/bruceasu/pg-eventbus/client/go/gateway
```

可在本地业务模块中通过 go.mod 的 replace 指向本仓库对应目录。直连使用 `New()` 或 `NewWithSchema("analytics")`，HTTP 使用 `New("http://localhost:8080")`。Go HTTP 默认超时 30 秒，支持 `NewWithHTTPClient`。

发布示例（函数体片段）：

```go
client := pgeventbus.New("http://localhost:8080") // 导入 client/go/gateway
published, err := client.Publish(ctx, "source-a", "consumer-a",
    "price-update", json.RawMessage(`{"price":123.45}`))
if err != nil {
    return err
}
_ = published
```

Go 直连的 NewWithRunner / NewWithRunnerAndSchema 支持注入实现 Run(ctx, sql) 的执行器。直连版 poll 不获得锁，commit 会检查数据库锁；调用方需额外编排锁，不能照搬 Java 的消费示例。

## Python 接入

在仓库根目录、独立虚拟环境中选择一个变体安装：

```powershell
python -m pip install -e ./client/python/db
# 或在另一个环境安装 HTTP 变体
python -m pip install -e ./client/python/gateway
```

HTTP 发布示例：

```python
from pg_eventbus import PgEventBusClient

client = PgEventBusClient("http://localhost:8080", timeout_seconds=30.0)
published = client.publish("source-a", "consumer-a", "price-update", {"price": 123.45})
print(published.event_id)
```

直连构造参数为 `command=None, tool_dir=None, timeout_seconds=30.0, runner=None, schema=None`；HTTP 构造参数为 `base_url, timeout_seconds=30.0`。HTTP 不接受 schema 参数，schema 在网关配置。

Python 显式的 psql/HTTP 错误包装为 `PgEventBusError`，但子进程超时、网络连接失败和 JSON 解析等异常不一定统一包装。直连命令解析与数据库连接配置见[配置文档](pg-eventbus-schema-and-cli-addendum.md#go--python-直连配置)。
