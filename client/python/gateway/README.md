# Python HTTP客户端

通过 HTTP 调用 pg-eventbus-gateway，无需本机 psql 或数据库账号。 模块/分发包为 `pg-eventbus-client-http`（Python 3.10+）。

## 当前状态

本目录可独立执行 `python -m unittest discover -s tests`，原有非法 DTO 类型限定名已修正。


## 接入与发布

从仓库根目录执行 `python -m pip install -e ./client/python/gateway`。直连和 HTTP 包均使用 `pg_eventbus` 导入名，请分别使用独立虚拟环境。

```python
from pg_eventbus import PgEventBusClient

client = PgEventBusClient("http://localhost:8080", timeout_seconds=30.0)
published = client.publish("source-a", "consumer-a", "price-update", {"price": 123.45})
print(published.event_id)
```

地址为网关根 URL，不包含 `/api/event-bus`。timeout_seconds 默认 30 秒。 schema 与消费参数由网关配置。

## 消费与维护

方法包括 publish、poll、commit、recover、run_maintenance、ensure_current_partition、ensure_future_partitions、drop_old_partitions 和 cleanup_consumed。recover 未提供时间时使用当前 UTC 时间，会重设位点。

目标名称须匹配消费者名称。处理完成后才提交末条已处理事件 ID，不要直接提交发布结果的 ID。提交是否成功要检查 committed 字段。

poll 通过网关获取租约，commit 要求同一实例持有未过期租约，提交不释放锁。未提供 limit 时采用网关批大小；平面 API 不自动处理回调、重试或死信。

显式的 psql/HTTP 错误使用 PgEventBusError；超时、网络或 JSON 解析异常不保证统一包装。

完整参数、DTO 和限制见[客户端指南](../../../docs/pg-eventbus-client-guide.md)，连接环境见[配置文档](../../../docs/pg-eventbus-schema-and-cli-addendum.md)。
