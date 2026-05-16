# pg-eventbus Go HTTP client

Flat HTTP client for `pg-eventbus-gateway`.

```go
client := pgeventbus.New("http://localhost:8080")
ctx := context.Background()

published, _ := client.Publish(ctx, "source", "target", "price-update", json.RawMessage(`{"price":123.45}`))
polled, _ := client.Poll(ctx, "consumer-a", "instance-a", nil)
committed, _ := client.Commit(ctx, "consumer-a", "instance-a", published.EventID)
recovered, _ := client.Recover(ctx, "consumer-a", time.Now().UTC())
maintained, _ := client.EnsureFuturePartitions(ctx)
```
