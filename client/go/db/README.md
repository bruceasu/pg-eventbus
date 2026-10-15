# Go 直连客户端

通过外部 psql 子进程执行 PostgreSQL SQL，不是原生数据库驱动。 模块/分发包为 `github.com/bruceasu/pg-eventbus/client/go/db`（Go 1.22）。

## 当前状态

本目录可独立执行 `go test ./...`，原有非法 DTO 类型限定名已修正。

直连版另有行为限制：poll 不获取消费者锁，返回的顶层 lockAcquired 固定为 true；查询 consumer_registry 要求额外表读权限；维护 SQL 的 CTE 结构存在问题。不能将其视为与 Java/网关完全等价的客户端。

## 接入与发布

在业务 go.mod 中使用该 module 路径；本地开发可用 replace 指向此目录。

```go
import (
    "context"
    "encoding/json"
    pgeventbus "github.com/bruceasu/pg-eventbus/client/go/db"
)

// 以下为函数体片段
client := pgeventbus.New()
published, err := client.Publish(context.Background(), "source-a", "consumer-a",
    "price-update", json.RawMessage(`{"price":123.45}`))
if err != nil {
    return err
}
_ = published
```

schema 优先级为显式 NewWithSchema 参数、`PGEVENTBUS_SCHEMA`、`bus`。

命令查找先使用 `PGEVENTBUS_PSQL_COMMAND`，再找 PATH 中的 psql，然后找工具目录中的 `bin/psql-go.exe` / `bin/psql-go`。工具目录来自 `PGEVENTBUS_PGSQL_CLIENT_DIR`；未指定时从工作目录向上寻找 `tools/pgsql-client`。标准 psql 可用 PGHOST、PGPORT、PGDATABASE、PGUSER 等环境变量连接数据库。

NewWithRunner / NewWithRunnerAndSchema 可注入 SQL 执行器。

## 消费与维护

方法包括 Publish、Poll、Commit、Recover、RunMaintenance、EnsureCurrentPartition、EnsureFuturePartitions、DropOldPartitions 和 CleanupConsumed，返回 (结果, error)。Recover 必须提供 time.Time。

目标名称须匹配消费者名称。处理完成后才提交末条已处理事件 ID，不要直接提交发布结果的 ID。提交是否成功要检查 Committed 字段。

直连 poll 不获取锁，commit 却要求持有数据库租约，调用方需要自行编排获取/续租/释放锁。仅 bootstrap 的函数授权不能满足 registry 表读取。

完整参数、DTO 和限制见[客户端指南](../../../docs/pg-eventbus-client-guide.md)，连接环境见[配置文档](../../../docs/pg-eventbus-schema-and-cli-addendum.md)。
