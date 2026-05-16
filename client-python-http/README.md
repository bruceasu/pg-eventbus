# pg-eventbus Python HTTP client

Flat HTTP client for `pg-eventbus-gateway`.

This package shares the `pg_eventbus` import name with the direct-DB variant, so install only one Python variant in a given environment.

```python
from pg_eventbus import PgEventBusClient

client = PgEventBusClient("http://localhost:8080")
published = client.publish("source", "target", "price-update", {"price": 123.45})
polled = client.poll("consumer-a", "instance-a", 100)
committed = client.commit("consumer-a", "instance-a", published.event_id)
recovered = client.recover("consumer-a")
maintained = client.ensure_future_partitions()
```
