package me.asu.pg_eventbus.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CliDatabaseTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PgEventBusProperties properties = new PgEventBusProperties();
    private final CliDatabase database = new CliDatabase(jdbc, properties);

    @Test
    void publishBindsPayloadAndQuotesConfiguredSchema() throws Exception {
        properties.setSchema("tenant\"one");
        when(jdbc.queryForObject("select \"tenant\"\"one\".\"publish_event\"(?, ?, ?, cast(? as jsonb))",
                Long.class, "source", "target", "created", "{\"value\":1}")).thenReturn(42L);
        assertEquals(42, database.publish("source", "target", "created", new ObjectMapper().readTree("{\"value\":1}")).eventId());
    }

    @Test
    void unsuccessfulLeaseDoesNotReadEvents() {
        var result = database.poll("consumer", "instance", null);
        assertFalse(result.lockAcquired());
        assertTrue(result.events().isEmpty());
        assertEquals(100, result.batchSize());
        verify(jdbc).queryForObject("select \"bus\".\"acquire_consumer_lock\"(?, ?, ?)", Boolean.class,
                "consumer", "instance", 30);
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void maintenanceExecutesBeforeReportingSuccessAndPropagatesFailure() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement("select \"bus\".\"ensure_future_partitions\"(?)")).thenReturn(statement);
        when(jdbc.execute(org.mockito.ArgumentMatchers.<ConnectionCallback<Void>>any())).thenAnswer(invocation -> {
            ConnectionCallback<Void> callback = invocation.getArgument(0);
            return callback.doInConnection(connection);
        });
        assertTrue(database.ensureFuturePartitions().executed());
        verify(statement).setObject(1, 6);
        verify(statement).execute();
        verify(statement).close();
        when(statement.execute()).thenThrow(new java.sql.SQLException("maintenance failed"));
        assertThrows(java.sql.SQLException.class, database::ensureFuturePartitions);
    }

    @Test
    void recoveryBindsTimestampInsteadOfDriverUnsupportedInstant() {
        Instant time = Instant.parse("2026-10-15T00:00:00Z");
        when(jdbc.queryForObject("select \"bus\".\"init_consumer_at_time\"(?, ?)", Long.class,
                "consumer", Timestamp.from(time))).thenReturn(9L);
        assertEquals(9L, database.recover("consumer", time).initializedOffset());
    }
}
