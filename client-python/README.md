# pg-eventbus Python direct-DB client

Flat direct-DB client for PostgreSQL.

Lookup order for the command used to talk to PostgreSQL:

1. `PGEVENTBUS_PSQL_COMMAND`
2. system `psql` on `PATH`
3. `PGEVENTBUS_PGSQL_CLIENT_DIR`
4. `<program dir>/tools/pgsql-client`

This package shares the `pg_eventbus` import name with the HTTP variant, so install only one Python variant in a given environment.

```python
from datetime import datetime, timezone
from pg_eventbus import PgEventBusClient

client = PgEventBusClient()
published = client.publish("source", "target", "price-update", {"price": 123.45})
polled = client.poll("consumer-a", "instance-a", 100)
committed = client.commit("consumer-a", "instance-a", published.event_id)
recovered = client.recover("consumer-a", datetime.now(timezone.utc))
maintenance = client.ensure_future_partitions()
```
