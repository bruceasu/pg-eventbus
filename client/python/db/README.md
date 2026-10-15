# Python 直连客户端

通过外部 psql 子进程执行 PostgreSQL SQL，不是原生数据库驱动。 模块/分发包为 `pg-eventbus-client`（Python 3.10+）。

## 当前状态

本目录可独立执行 `python -m unittest discover -s tests`，原有非法 DTO 类型限定名已修正。

直连版另有行为限制：poll 不获取消费者锁，返回的顶层 lockAcquired 固定为 true；查询 consumer_registry 要求额外表读权限；维护 SQL 的 CTE 结构存在问题。不能将其视为与 Java/网关完全等价的客户端。

## 接入与发布

从仓库根目录执行 `python -m pip install -e ./client/python/db`。直连和 HTTP 包均使用 `pg_eventbus` 导入名，请分别使用独立虚拟环境。

```python
from pg_eventbus import PgEventBusClient

client = PgEventBusClient(schema="bus")
published = client.publish("source-a", "consumer-a", "price-update", {"price": 123.45})
print(published.event_id)
```

schema 优先级为显式 schema 参数、`PGEVENTBUS_SCHEMA`、`bus`。

命令查找先使用 构造参数 command 或 `PGEVENTBUS_PSQL_COMMAND`，再找 PATH 中的 psql，然后找工具目录中的 `bin/psql-go.exe` / `bin/psql-go`。工具目录来自 tool_dir 或 `PGEVENTBUS_PGSQL_CLIENT_DIR`；未指定时从工作目录向上寻找 `tools/pgsql-client`。标准 psql 可用 PGHOST、PGPORT、PGDATABASE、PGUSER 等环境变量连接数据库。

构造参数还包括 timeout_seconds（默认 30 秒）和 runner；command 只能是可执行文件路径，不能附带命令行参数。

## 消费与维护

方法包括 publish、poll、commit、recover、run_maintenance、ensure_current_partition、ensure_future_partitions、drop_old_partitions 和 cleanup_consumed。recover 未提供时间时使用当前 UTC 时间，会重设位点。

目标名称须匹配消费者名称。处理完成后才提交末条已处理事件 ID，不要直接提交发布结果的 ID。提交是否成功要检查 committed 字段。

直连 poll 不获取锁，commit 却要求持有数据库租约，调用方需要自行编排获取/续租/释放锁。仅 bootstrap 的函数授权不能满足 registry 表读取。

显式的 psql/HTTP 错误使用 PgEventBusError；超时、网络或 JSON 解析异常不保证统一包装。

完整参数、DTO 和限制见[客户端指南](../../../docs/pg-eventbus-client-guide.md)，连接环境见[配置文档](../../../docs/pg-eventbus-schema-and-cli-addendum.md)。
