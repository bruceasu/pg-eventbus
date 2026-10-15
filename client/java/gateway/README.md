# Java HTTP 客户端

通过 JDK HttpClient 调用 `pg-eventbus-gateway`，客户端无需数据库连接。构建目标为 JDK 25。

## 依赖

先在仓库根目录构建安装 Maven 模块，再引入：

```xml
<dependency>
    <groupId>me.asu</groupId>
    <artifactId>pg-eventbus-client-java-http</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

## 发布

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import me.asu.pg_eventbus.client.http.PgEventBusHttpClient;
import me.asu.pg_eventbus.client.http.model.PublishResultDto;

var client = new PgEventBusHttpClient("http://localhost:8080");
PublishResultDto published = client.publish(
        "source-a", "consumer-a", "price-update",
        new ObjectMapper().createObjectNode().put("price", 123.45));
```

传入网关根 URL，不能包含 API 路径。重载构造器允许注入 HttpClient 和 ObjectMapper；默认构造器没有显式请求超时。

## 消费与维护

方法为 `poll`、`commit`、`recover`、`runMaintenance`、`ensureCurrentPartition`、`ensureFuturePartitions`、`dropOldPartitions(int)` 和 `cleanupConsumed`。客户端解包 ApiResponse，返回 `me.asu.pg_eventbus.client.http.model` 下的 DTO，清理返回 int。

poll 后检查 `lockAcquired()`，处理成功后提交最后已处理事件 ID，并检查 `committed()`。提交不释放租约；网关未提供续租/释放接口。recover 必须提供 Instant，且会重设位点。

HTTP 非 2xx、失败信封和 I/O 错误抛出 IllegalStateException。schema、批大小、锁 TTL 和重试阈值在网关配置。详见[客户端指南](../../../docs/pg-eventbus-client-guide.md)与[网关 API](../../../README.md#http-网关)。
