# pg-eventbus Java HTTP client

Flat HTTP client for `pg-eventbus-gateway`.

```java
PgEventBusHttpClient client = new PgEventBusHttpClient("http://localhost:8080");

contract.me.asu.pg_eventbus.PublishResultDto published = client.publish("market", "risk", "price-update", payload);
contract.me.asu.pg_eventbus.PollResultDto polled = client.poll("consumer-a", "instance-a", 100);
contract.me.asu.pg_eventbus.CommitResultDto committed = client.commit("consumer-a", "instance-a", published.eventId());
contract.me.asu.pg_eventbus.RecoverResultDto recovered = client.recover("consumer-a", Instant.parse("2026-05-16T00:00:00Z"));
contract.me.asu.pg_eventbus.MaintenanceResultDto maintained = client.ensureFuturePartitions();
```