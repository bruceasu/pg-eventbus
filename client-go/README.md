# pg-eventbus Go direct-DB client

Flat direct-DB client for PostgreSQL.

Lookup order for the command used to talk to PostgreSQL:

1. `PGEVENTBUS_PSQL_COMMAND`
2. system `psql` on `PATH`
3. `PGEVENTBUS_PGSQL_CLIENT_DIR`
4. `<program dir>/tools/pgsql-client`

```go
client := pgeventbus.New()
ctx := context.Background()

published, _ := client.Publish(ctx, "source", "target", "price-update", json.RawMessage(`{"price":123.45}`))
polled, _ := client.Poll(ctx, "consumer-a", "instance-a", nil)
committed, _ := client.Commit(ctx, "consumer-a", "instance-a", published.EventID)
recovered, _ := client.Recover(ctx, "consumer-a", time.Now().UTC())
maintenance, _ := client.RunMaintenance(ctx)
```
