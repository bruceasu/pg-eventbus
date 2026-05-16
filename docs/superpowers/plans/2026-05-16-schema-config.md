# Schema Configuration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the PostgreSQL schema configurable across Java, Go, and Python clients while keeping `bus` as the default.

**Architecture:** Introduce a schema setting at the Spring properties layer and thread it through SQL generation in the Java JDBC repository, Go direct client, and Python direct client. Keep the public method signatures stable where possible by adding optional configuration at construction time or via environment variables, and centralize schema normalization so every SQL statement uses the same validated prefix.

**Tech Stack:** Java Spring Boot, Go, Python, PostgreSQL SQL string generation, existing unit tests.

---

### Task 1: Add schema property and plumbing in Java

**Files:**
- Modify: `pg-eventbus-core/src/main/java/me/asu/pg_eventbus/core/PgEventBusProperties.java`
- Modify: `pg-eventbus-jdbc/src/main/java/me/asu/pg_eventbus/jdbc/JdbcPgEventBusRepository.java`
- Modify: `pg-eventbus-core/src/test/java/me/asu/pg_eventbus/core/PgEventBusCoreServicesTest.java`
- Modify: `pg-eventbus-jdbc/src/test/java/me/asu/pg_eventbus/jdbc/JdbcPgEventBusRepositoryTest.java`

- [ ] **Step 1: Extend the properties record with `schema`, defaulting to `bus`**

```java
public record PgEventBusProperties(
        boolean enabled,
        String consumerName,
        int batchSize,
        int lockTtlSeconds,
        int maxRetries,
        String schema
) {
    public PgEventBusProperties {
        schema = (schema == null || schema.isBlank()) ? "bus" : schema.trim();
    }
}
```

- [ ] **Step 2: Add a small schema helper in `JdbcPgEventBusRepository` and replace every hard-coded `bus.` reference**

```java
private final String schema;

public JdbcPgEventBusRepository(
        NamedParameterJdbcTemplate namedParameterJdbcTemplate,
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper,
        PgEventBusProperties properties
) {
    this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
    this.schema = normalizeSchema(properties.schema());
}

private String qualify(String function) {
    return schema + "." + function;
}

private static String normalizeSchema(String schema) {
    String value = schema == null || schema.isBlank() ? "bus" : schema.trim();
    if (!value.matches("[A-Za-z_][A-Za-z0-9_]*")) {
        throw new IllegalArgumentException("Invalid PostgreSQL schema: " + value);
    }
    return value;
}
```

- [ ] **Step 3: Update unit tests to verify default `bus` and custom schema SQL generation**

```java
PgEventBusProperties properties = new PgEventBusProperties(true, "consumer-a", 100, 30, 3, "custom_bus");
JdbcPgEventBusRepository repository = new JdbcPgEventBusRepository(namedParameterJdbcTemplate, jdbcTemplate, objectMapper, properties);
repository.publishEvent("source", "target", "event", objectMapper.createObjectNode());
verify(namedParameterJdbcTemplate).queryForObject(startsWith("select bus.publish_event"), any(MapSqlParameterSource.class), eq(Long.class));
```

### Task 2: Thread schema config through Spring wiring

**Files:**
- Modify: `pg-eventbus-gateway/src/main/java/me/asu/pg_eventbus/gateway/config/PgEventBusClientConfiguration.java`
- Modify: `pg-eventbus-cli/src/main/java/me/asu/pg_eventbus/cli/config/PgEventBusClientConfiguration.java`
- Modify: `pg-eventbus-gateway/src/main/java/me/asu/pg_eventbus/gateway/PgEventBusGatewayApplication.java` if needed for property binding
- Modify: `pg-eventbus-cli/src/main/java/me/asu/pg_eventbus/cli/...` configuration if there is a binding hook

- [ ] **Step 1: Inspect existing Spring bean creation and add `PgEventBusProperties` injection where the repository is created**

```java
@Bean
public PgEventBusClient pgEventBusClient(
        PgEventBusPublisherService publisherService,
        PgEventBusConsumerPollService pollService,
        PgEventBusConsumerRecoveryService recoveryService,
        PgEventBusConsumerCommitService commitService,
        PgEventBusMaintenanceService maintenanceService
) {
    return new PgEventBusClient(publisherService, pollService, recoveryService, commitService, maintenanceService);
}
```

- [ ] **Step 2: Ensure Spring binds `bus.schema` with default `bus`**

```yaml
market-intel:
  bus:
    enabled: true
    schema: bus
```

- [ ] **Step 3: Add a configuration test or update an existing one if wiring changes**

```java
assertEquals("bus", new PgEventBusProperties(true, "c", 10, 30, 3, null).schema());
assertEquals("alt_schema", new PgEventBusProperties(true, "c", 10, 30, 3, "alt_schema").schema());
```

### Task 3: Add schema config to Go direct client

**Files:**
- Modify: `client-go/client.go`
- Modify: `client-go/types.go` if needed
- Modify: `client-go/client_test.go`
- Modify: `client-go/README.md`
- Modify: `client-go-http/client.go` only if HTTP client also needs schema-aware paths (unlikely)

- [ ] **Step 1: Add `schema` to `Client`, defaulting via env or constructor**

```go
type Client struct {
    runner runner
    schema string
}

func New() *Client {
    return &Client{runner: newDefaultRunner(), schema: resolveSchema("")}
}
```

- [ ] **Step 2: Replace hard-coded `bus.` in SQL builders with a qualified helper**

```go
func (c *Client) qualify(name string) string {
    return c.schema + "." + name
}
```

- [ ] **Step 3: Add tests for default `bus` and custom schema**

```go
client := NewWithRunner(runner)
client.schema = "alt_schema"
_, _ = client.RunMaintenance(ctx)
if !strings.Contains(runner.lastSQL, "alt_schema.maintenance") {
    t.Fatalf("expected custom schema in SQL: %s", runner.lastSQL)
}
```

### Task 4: Add schema config to Python direct client

**Files:**
- Modify: `client-python/src/pg_eventbus/client.py`
- Modify: `client-python/tests/test_client.py`
- Modify: `client-python/README.md`

- [ ] **Step 1: Add optional schema argument and `PGEVENTBUS_SCHEMA` env fallback**

```python
class PgEventBusClient:
    def __init__(self, command: str | None = None, tool_dir: str | None = None, timeout_seconds: float = 30.0, runner: _Runner | None = None, schema: str | None = None):
        self._schema = _resolve_schema(schema)
```

- [ ] **Step 2: Route SQL generation through a qualified helper**

```python
def _qualify(schema: str, name: str) -> str:
    return f"{schema}.{name}"
```

- [ ] **Step 3: Update tests to cover the default and overridden schema**

```python
client = PgEventBusClient(runner=FakeRunner(), schema="alt_schema")
client.run_maintenance()
self.assertIn("alt_schema.maintenance", client._runner.last_sql)
```

### Task 5: Update docs and validation

**Files:**
- Modify: `docs/pg-eventbus-client-guide.md`
- Modify: `README.md`
- Modify: `client-go/README.md`
- Modify: `client-python/README.md`
- Modify: `pg-eventbus-client-java-http/README.md` if you want parity notes

- [ ] **Step 1: Document `bus.schema`, `PGEVENTBUS_SCHEMA`, and the default `bus`**

```yaml
market-intel:
  bus:
    enabled: true
    schema: bus
```

- [ ] **Step 2: Document that schema names are validated and must be legal PostgreSQL identifiers**

```text
Default: bus
Override examples: analytics_bus, eventbus_prod
```

- [ ] **Step 3: Run the relevant tests**

```powershell
mvn -pl pg-eventbus-core,pg-eventbus-jdbc,pg-eventbus-gateway,pg-eventbus-cli -am test
go test ./...
python -m unittest
```