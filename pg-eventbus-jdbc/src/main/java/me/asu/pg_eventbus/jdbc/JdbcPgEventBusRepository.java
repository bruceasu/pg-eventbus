package me.asu.pg_eventbus.jdbc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.asu.pg_eventbus.jdbc.model.DeadLetterRow;
import me.asu.pg_eventbus.jdbc.model.EventRow;
import me.asu.pg_eventbus.jdbc.model.RetryRow;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

@Repository
@ConditionalOnProperty(prefix = "bus", name = "enabled", havingValue = "true")
@ConditionalOnBean(NamedParameterJdbcTemplate.class)
public class JdbcPgEventBusRepository implements PgEventBusRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String schema;

    public JdbcPgEventBusRepository(
            NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this(namedParameterJdbcTemplate, jdbcTemplate, objectMapper, "bus");
    }

    @Autowired
    public JdbcPgEventBusRepository(
            NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${bus.schema:bus}") String schema
    ) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.schema = normalizeSchema(schema);
    }

    @Override
    public long publishEvent(String sourceSystem, String targetSystem, String eventType, JsonNode payload) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("sourceSystem", sourceSystem)
                .addValue("targetSystem", targetSystem)
                .addValue("eventType", eventType)
                .addValue("payload", payload == null ? "null" : payload.toString());

        Long result = namedParameterJdbcTemplate.queryForObject(
                "select " + qualifiedFunction("publish_event") + "(:sourceSystem, :targetSystem, :eventType, cast(:payload as jsonb))",
                params,
                Long.class
        );
        return result == null ? 0L : result;
    }

    @Override
    public void registerConsumer(String consumerName) {
        callVoidFunction("select " + qualifiedFunction("register_consumer") + "(?)", consumerName);
    }

    @Override
    public long initConsumerAtTime(String consumerName, Instant startTime) {
        Long result = jdbcTemplate.queryForObject(
                "select " + qualifiedFunction("init_consumer_at_time") + "(?, ?)",
                Long.class,
                consumerName,
                startTime
        );
        return result == null ? 0L : result;
    }

    @Override
    public boolean acquireConsumerLock(String consumerName, String instanceId, int ttlSeconds) {
        Boolean result = jdbcTemplate.queryForObject(
                "select " + qualifiedFunction("acquire_consumer_lock") + "(?, ?, ?)",
                Boolean.class,
                consumerName,
                instanceId,
                ttlSeconds
        );
        return Boolean.TRUE.equals(result);
    }

    @Override
    public boolean renewConsumerLock(String consumerName, String instanceId, int ttlSeconds) {
        Boolean result = jdbcTemplate.queryForObject(
                "select " + qualifiedFunction("renew_consumer_lock") + "(?, ?, ?)",
                Boolean.class,
                consumerName,
                instanceId,
                ttlSeconds
        );
        return Boolean.TRUE.equals(result);
    }

    @Override
    public boolean releaseConsumerLock(String consumerName, String instanceId) {
        Boolean result = jdbcTemplate.queryForObject(
                "select " + qualifiedFunction("release_consumer_lock") + "(?, ?)",
                Boolean.class,
                consumerName,
                instanceId
        );
        return Boolean.TRUE.equals(result);
    }

    @Override
    public List<EventRow> pollEvents(String consumerName, int limit) {
        return namedParameterJdbcTemplate.query(
                "select * from " + qualifiedFunction("poll_events") + "(:consumerName, :limit)",
                new MapSqlParameterSource()
                        .addValue("consumerName", consumerName)
                        .addValue("limit", limit),
                eventRowMapper()
        );
    }

    @Override
    public boolean commitConsumerOffset(String consumerName, String instanceId, long newLastId) {
        Boolean result = jdbcTemplate.queryForObject(
                "select " + qualifiedFunction("commit_consumer_offset") + "(?, ?, ?)",
                Boolean.class,
                consumerName,
                instanceId,
                newLastId
        );
        return Boolean.TRUE.equals(result);
    }

    @Override
    public void scheduleRetry(long originalId, String consumerName, JsonNode payload, int retryCount) {
        callVoidFunctionWithJson(
                "select " + qualifiedFunction("schedule_retry") + "(?, ?, cast(? as jsonb), ?)",
                originalId,
                consumerName,
                payload,
                retryCount
        );
    }

    @Override
    public void moveToDeadLetter(long originalId, String consumerName, JsonNode payload, String errorMessage) {
        callVoidFunctionWithJson(
                "select " + qualifiedFunction("move_to_deadletter") + "(?, ?, cast(? as jsonb), ?)",
                originalId,
                consumerName,
                payload,
                errorMessage
        );
    }

    @Override
    public List<RetryRow> pollRetry(String consumerName, int limit) {
        return namedParameterJdbcTemplate.query(
                "select * from " + qualifiedFunction("poll_retry") + "(:consumerName, :limit)",
                new MapSqlParameterSource()
                        .addValue("consumerName", consumerName)
                        .addValue("limit", limit),
                (rs, rowNum) -> new RetryRow(
                        rs.getLong("retry_id"),
                        rs.getLong("original_id"),
                        readJson(rs.getString("payload")),
                        rs.getInt("retry_count")
                )
        );
    }

    @Override
    public void deleteRetry(long retryId) {
        callVoidFunction("select " + qualifiedFunction("delete_retry") + "(?)", retryId);
    }

    @Override
    public List<DeadLetterRow> pollDeadLetters(String consumerName, int limit) {
        return namedParameterJdbcTemplate.query(
                "select * from " + qualifiedFunction("poll_deadletter") + "(:consumerName, :limit)",
                new MapSqlParameterSource()
                        .addValue("consumerName", consumerName)
                        .addValue("limit", limit),
                (rs, rowNum) -> new DeadLetterRow(
                        rs.getLong("dead_id"),
                        rs.getObject("original_id", Long.class),
                        readJson(rs.getString("payload")),
                        rs.getString("error_message"),
                        rs.getTimestamp("failed_at").toInstant()
                )
        );
    }

    @Override
    public void ensureCurrentPartition() {
        callVoidFunction("select " + qualifiedFunction("ensure_current_partition") + "()");
    }

    @Override
    public void ensureFuturePartitions(int monthsAhead) {
        callVoidFunction("select " + qualifiedFunction("ensure_future_partitions") + "(?)", monthsAhead);
    }

    @Override
    public int cleanupConsumed(int batchSize) {
        Integer result = jdbcTemplate.queryForObject(
                "select " + qualifiedFunction("cleanup_consumed") + "(?)",
                Integer.class,
                batchSize
        );
        return result == null ? 0 : result;
    }

    @Override
    public void dropPartitionOlderThan(int keepMonths) {
        callVoidFunction("select " + qualifiedFunction("drop_partition_older_than") + "(?)", keepMonths);
    }

    @Override
    public void maintenance() {
        callVoidFunction("select " + qualifiedFunction("maintenance") + "()");
    }

    private String qualifiedFunction(String functionName) {
        return quoteIdentifier(schema) + "." + quoteIdentifier(functionName);
    }

    private static String normalizeSchema(String schema) {
        if (schema == null || schema.isBlank()) {
            return "bus";
        }
        return schema.trim();
    }

    private static String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private RowMapper<EventRow> eventRowMapper() {
        return (rs, rowNum) -> new EventRow(
                rs.getLong("id"),
                rs.getString("source_system"),
                rs.getString("target_system"),
                rs.getString("event_type"),
                readJson(rs.getString("payload")),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private void callVoidFunction(String sql, Object... args) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, args);
                statement.execute();
                return null;
            }
        });
    }

    private void callVoidFunctionWithJson(String sql, Object... args) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bindJsonAware(statement, args);
                statement.execute();
                return null;
            }
        });
    }

    private void bind(PreparedStatement statement, Object... args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            statement.setObject(i + 1, args[i]);
        }
    }

    private void bindJsonAware(PreparedStatement statement, Object... args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            Object value = args[i];
            if (value instanceof JsonNode jsonNode) {
                statement.setObject(i + 1, jsonNode.toString());
            } else {
                statement.setObject(i + 1, value);
            }
        }
    }

    private JsonNode readJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return objectMapper.nullNode();
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse JSON payload from PostgreSQL", ex);
        }
    }
}