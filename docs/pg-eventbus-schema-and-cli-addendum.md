# pg-eventbus schema and CLI addendum

This note fills in the operational pieces needed by downstream projects:

- how to switch the PostgreSQL schema away from the default `bus`
- how to bootstrap an empty database
- how to run partition maintenance
- how the CLI command names map to the event operations

## 1. Schema configuration

The default schema is `bus`.

### Java Spring Boot

```yaml
bus:
    enabled: true
    schema: bus
```

Equivalent environment variables:

```bash
MARKET_INTEL_BUS_ENABLED=true
MARKET_INTEL_BUS_SCHEMA=bus
```

### Go / Python direct clients

Both direct clients accept an explicit `schema` argument. If it is omitted, they read `PGEVENTBUS_SCHEMA` first and fall back to `bus`.

```bash
PGEVENTBUS_SCHEMA=analytics
```

## 2. Database bootstrap

`db init` bootstraps an empty database for the configured schema.

Behavior:

- creates the schema if it does not exist
- creates the event bus tables and functions
- grants schema/function access to the requested owner
- ensures the current partition exists
- ensures the next 6 months of partitions exist

Example:

```powershell
pg-eventbus-cli db init app_role
```

Owner can also come from:

- the third positional argument
- `PGEVENTBUS_DB_OWNER`

The schema comes from `bus.schema` or `PGEVENTBUS_SCHEMA`.

## 3. Maintenance

`maintenance run` executes the maintenance function in the database.

Current maintenance behavior:

- ensures the next 6 months of partitions
- drops partitions older than the configured retention window

Example:

```powershell
pg-eventbus-cli maintenance run
```

## 4. Event commands

The CLI supports both the new command names and the older aliases:

- `event send` or `event publish`
- `event get` or `event poll`
- `event set-cursor` or `event commit`

Examples:

```powershell
pg-eventbus-cli event send source-a target-a price-update "{\"price\":123.45}"
pg-eventbus-cli event get consumer-a instance-a 100
pg-eventbus-cli event set-cursor consumer-a instance-a 12345
```

## 5. Spring Boot beans

If you wire the JDBC layer manually, the minimal beans are:

- `DataSource`
- `JdbcTemplate`
- `NamedParameterJdbcTemplate`

If you use Spring Boot standard datasource configuration, the JDBC layer can be created automatically from `spring.datasource.*`.