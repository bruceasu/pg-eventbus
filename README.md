# pg-eventbus

Standalone PostgreSQL event bus workspace.

## Quick Choice

If you only need one rule, use this:

- **Can the caller reach PostgreSQL directly?** Use a direct-DB client.
- **Cannot reach PostgreSQL directly?** Use the HTTP gateway client.
- **Need shell/operator actions?** Use the CLI.

## Client Map

| Scenario | Connectivity | Language | Module | Notes |
|---|---|---|---|---|
| Application code | Direct DB | Java | `pg-eventbus-client-java` | Used by the main `market-intel` app |
| Application code | Direct DB | Go | `client-go` | Resolves `psql` first, then `tools/pgsql-client` |
| Application code | Direct DB | Python | `client-python` | Resolves `psql` first, then `tools/pgsql-client` |
| Application code | HTTP gateway | Java | `pg-eventbus-client-java-http` | Flat API over `pg-eventbus-gateway` |
| Application code | HTTP gateway | Go | `client-go-http` | Flat API over `pg-eventbus-gateway` |
| Application code | HTTP gateway | Python | `client-python-http` | Flat API over `pg-eventbus-gateway` |
| Operations / automation | Direct DB | Any | `pg-eventbus-cli` | For DB init, partitions, maintenance, publish, poll |
| Services without DB access | HTTP gateway | Any | `pg-eventbus-gateway` | Network API in front of PostgreSQL |

## Default Recommendation

- **Best default for this repo:** `pg-eventbus-client-java`
- **Best default when DB access is blocked:** `pg-eventbus-client-java-http`
- **Best default for scripts and operators:** `pg-eventbus-cli`
- **Best default for Go / Python services with DB access:** `client-go` / `client-python`

## Direct-DB Resolution Order

The direct-DB Go and Python clients look for the PostgreSQL command in this order:

1. `PGEVENTBUS_PSQL_COMMAND`
2. system `psql` on `PATH`
3. `PGEVENTBUS_PGSQL_CLIENT_DIR`
4. `D:/03_projects/market-intel/tools/pgsql-client`

## Python Import Note

`client-python` and `client-python-http` both expose the `pg_eventbus` import name, so install only one Python variant in a given environment.

## Module Summary

- `pg-eventbus-contract`: public DTO contract
- `pg-eventbus-jdbc`: PostgreSQL function adapter
- `pg-eventbus-core`: orchestration and business flow
- `pg-eventbus-client-java`: Java embedding client for local Maven install
- `pg-eventbus-client-java-http`: Java flat HTTP client for `pg-eventbus-gateway`
- `pg-eventbus-cli`: operator CLI
- `pg-eventbus-gateway`: HTTP gateway for callers that cannot reach PostgreSQL directly
- `client-go`: Go direct-DB client
- `client-go-http`: Go HTTP client for the gateway
- `client-python`: Python direct-DB client
- `client-python-http`: Python HTTP client for the gateway

## Client Guide

If you want a single page that explains the `pg-eventbus-client` interfaces and usage across Java / Go / Python, start here:

- [pg-eventbus-client 接口与使用说明](docs/pg-eventbus-client-guide.md)
- [Schema and CLI addendum](docs/pg-eventbus-schema-and-cli-addendum.md)

## Minimal Examples

### Java direct DB

```java
PgEventBusClient client = new PgEventBusClient(...);
contract.me.asu.pg_eventbus.PublishResultDto published = client.publish("market", "risk", "price-update", payload);
```

### Java HTTP

```java
PgEventBusHttpClient client = new PgEventBusHttpClient("http://localhost:8080");
contract.me.asu.pg_eventbus.PublishResultDto published = client.publish("market", "risk", "price-update", payload);
```

### Go direct DB

```go
client := pgeventbus.New()
published, _ := client.Publish(ctx, "market", "risk", "price-update", json.RawMessage(`{"price":123.45}`))
```

### Go HTTP

```go
client := pgeventbus.New("http://localhost:8080")
published, _ := client.Publish(ctx, "market", "risk", "price-update", json.RawMessage(`{"price":123.45}`))
```

### Python direct DB

```python
client = PgEventBusClient()
published = client.publish("market", "risk", "price-update", {"price": 123.45})
```

### Python HTTP

```python
client = PgEventBusClient("http://localhost:8080")
published = client.publish("market", "risk", "price-update", {"price": 123.45})
```

## CLI Examples

```powershell
pg-eventbus-cli db init app_role
pg-eventbus-cli partition ensure-current
pg-eventbus-cli partition ensure-future
pg-eventbus-cli partition drop-older-than 3
pg-eventbus-cli event send source-a target-a price-update "{\"price\":123.45}"
pg-eventbus-cli event get consumer-a instance-a 100
pg-eventbus-cli event set-cursor consumer-a instance-a 12345
pg-eventbus-cli event recover consumer-a 2026-05-16T00:00:00Z
pg-eventbus-cli maintenance run
```

To switch away from the default `bus` schema, set:

```yaml
market-intel:
  bus:
    schema: analytics
```

## Gateway Endpoints

```http
POST /api/event-bus/events
POST /api/event-bus/consumers/{consumerName}/poll
POST /api/event-bus/consumers/{consumerName}/commit
POST /api/event-bus/consumers/{consumerName}/recover
POST /api/event-bus/maintenance/run
POST /api/event-bus/maintenance/ensure-current-partition
POST /api/event-bus/maintenance/ensure-future-partitions
POST /api/event-bus/maintenance/drop-older-than/{keepMonths}
POST /api/event-bus/maintenance/cleanup-consumed
```
