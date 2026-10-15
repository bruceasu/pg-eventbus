package me.asu.pg_eventbus.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.asu.pg_eventbus.cli.model.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/** CLI-owned SQL calls. No client library, ORM, or project module dependencies. */
@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class CliDatabase {
    private final JdbcTemplate jdbc;
    private final PgEventBusProperties properties;
    private final ObjectMapper json = new ObjectMapper();

    public CliDatabase(JdbcTemplate jdbc, PgEventBusProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    private String function(String name) {
        return "\"" + properties.schema().replace("\"", "\"\"") + "\".\"" + name + "\"";
    }

    public PublishResultDto publish(String source, String target, String type, com.fasterxml.jackson.databind.JsonNode payload) {
        Long id = jdbc.queryForObject("select " + function("publish_event") + "(?, ?, ?, cast(? as jsonb))",
                Long.class, source, target, type, payload.toString());
        return new PublishResultDto(id == null ? 0 : id);
    }

    public PollResultDto poll(String consumer, String instance, Integer requestedLimit) {
        int limit = requestedLimit == null || requestedLimit <= 0 ? properties.batchSize() : requestedLimit;
        boolean locked = Boolean.TRUE.equals(jdbc.queryForObject(
                "select " + function("acquire_consumer_lock") + "(?, ?, ?)", Boolean.class,
                consumer, instance, properties.lockTtlSeconds()));
        ConsumerDto state = consumer(consumer, instance, locked, limit, 0);
        if (!locked) return new PollResultDto(state, List.of(), limit, false, Instant.now());
        List<EventDto> events = jdbc.query("select * from " + function("poll_events") + "(?, ?)",
                (rs, index) -> {
                    try {
                        return new EventDto(rs.getLong("id"), rs.getString("source_system"),
                                rs.getString("target_system"), rs.getString("event_type"),
                                json.readTree(rs.getString("payload")), rs.getTimestamp("created_at").toInstant());
                    } catch (java.io.IOException ex) {
                        throw new IllegalStateException("Invalid event JSON", ex);
                    }
                }, consumer, limit);
        return new PollResultDto(state, events, limit, true, Instant.now());
    }

    public CommitResultDto commit(String consumer, String instance, long offset) {
        boolean committed = Boolean.TRUE.equals(jdbc.queryForObject(
                "select " + function("commit_consumer_offset") + "(?, ?, ?)", Boolean.class,
                consumer, instance, offset));
        return new CommitResultDto(committed, offset);
    }

    public RecoverResultDto recover(String consumer, Instant time) {
        Long offset = jdbc.queryForObject("select " + function("init_consumer_at_time") + "(?, ?)",
                Long.class, consumer, Timestamp.from(time));
        long initialized = offset == null ? 0 : offset;
        return new RecoverResultDto(initialized, consumer(consumer, "", false, properties.batchSize(), initialized));
    }

    public MaintenanceResultDto ensureCurrentPartition() {
        return execute("ensure_current_partition", "ensure_current_partition");
    }

    public MaintenanceResultDto ensureFuturePartitions() {
        return execute("ensure_future_partitions", "ensure_future_partitions:6", 6);
    }

    public MaintenanceResultDto dropOldPartitions(int months) {
        return execute("drop_partition_older_than", "drop_partition_older_than:" + months, months);
    }

    public MaintenanceResultDto runMaintenance() {
        return execute("maintenance", "maintenance");
    }

    private MaintenanceResultDto execute(String name, String action, Object... args) {
        String placeholders = String.join(", ", java.util.Collections.nCopies(args.length, "?"));
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("select " + function(name) + "(" + placeholders + ")")) {
                for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
                statement.execute();
            }
            return null;
        });
        return new MaintenanceResultDto(true, List.of(action), Instant.now());
    }

    private ConsumerDto consumer(String name, String instance, boolean locked, int limit, long offset) {
        return new ConsumerDto(name, instance, locked, locked ? instance : null,
                null, null, offset, offset, limit);
    }
}
