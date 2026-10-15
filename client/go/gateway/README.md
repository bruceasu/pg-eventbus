# Go HTTP客户端

通过 HTTP 调用 pg-eventbus-gateway，无需本机 psql 或数据库账号。 模块/分发包为 `github.com/bruceasu/pg-eventbus/client/go/gateway`（Go 1.22）。

## 当前状态

本目录可独立执行 `go test ./...`，原有非法 DTO 类型限定名已修正。


## 接入与发布

在业务 go.mod 中使用该 module 路径；本地开发可用 replace 指向此目录。

```go
import (
    "context"
    "encoding/json"
    pgeventbus "github.com/bruceasu/pg-eventbus/client/go/gateway"
)

// 以下为函数体片段
client := pgeventbus.New("http://localhost:8080")
published, err := client.Publish(context.Background(), "source-a", "consumer-a",
    "price-update", json.RawMessage(`{"price":123.45}`))
if err != nil {
    return err
}
_ = published
```

地址为网关根 URL，不包含 `/api/event-bus`。默认 HTTP 超时 30 秒；NewWithHTTPClient 可注入客户端。 schema 与消费参数由网关配置。

## 消费与维护

方法包括 Publish、Poll、Commit、Recover、RunMaintenance、EnsureCurrentPartition、EnsureFuturePartitions、DropOldPartitions 和 CleanupConsumed，返回 (结果, error)。Recover 必须提供 time.Time。

目标名称须匹配消费者名称。处理完成后才提交末条已处理事件 ID，不要直接提交发布结果的 ID。提交是否成功要检查 Committed 字段。

poll 通过网关获取租约，commit 要求同一实例持有未过期租约，提交不释放锁。未提供 limit 时采用网关批大小；平面 API 不自动处理回调、重试或死信。

完整参数、DTO 和限制见[客户端指南](../../../docs/pg-eventbus-client-guide.md)，连接环境见[配置文档](../../../docs/pg-eventbus-schema-and-cli-addendum.md)。
