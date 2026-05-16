package me.asu.pg_eventbus.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcPgEventBusRepositoryTest {

    @Mock
    NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Mock
    JdbcTemplate jdbcTemplate;

    ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void publishEventUsesTheBusFunctionAndReturnsTheDatabaseId() {
        when(namedParameterJdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(101L);
        JdbcPgEventBusRepository repository = new JdbcPgEventBusRepository(namedParameterJdbcTemplate, jdbcTemplate, objectMapper);

        long eventId = repository.publishEvent("source", "target", "type", objectMapper.createObjectNode().put("k", "v"));

        assertEquals(101L, eventId);
        var sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(namedParameterJdbcTemplate).queryForObject(sqlCaptor.capture(), any(MapSqlParameterSource.class), eq(Long.class));
        assertTrue(sqlCaptor.getValue().contains("\"bus\".\"publish_event\""));
    }

    @Test
    void publishEventUsesTheConfiguredSchemaWhenProvided() {
        when(namedParameterJdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(101L);
        JdbcPgEventBusRepository repository = new JdbcPgEventBusRepository(namedParameterJdbcTemplate, jdbcTemplate, objectMapper, "analytics");

        long eventId = repository.publishEvent("source", "target", "type", objectMapper.createObjectNode().put("k", "v"));

        assertEquals(101L, eventId);
        var sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(namedParameterJdbcTemplate).queryForObject(sqlCaptor.capture(), any(MapSqlParameterSource.class), eq(Long.class));
        assertTrue(sqlCaptor.getValue().contains("\"analytics\".\"publish_event\""));
    }

    @Test
    @SuppressWarnings("unchecked")
    void pollEventsMapsTheResultSetIntoEventRows() throws Exception {
        when(namedParameterJdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(2);
                    ResultSet resultSet = org.mockito.Mockito.mock(ResultSet.class);
                    when(resultSet.getLong("id")).thenReturn(7L);
                    when(resultSet.getString("source_system")).thenReturn("source");
                    when(resultSet.getString("target_system")).thenReturn("target");
                    when(resultSet.getString("event_type")).thenReturn("type");
                    when(resultSet.getString("payload")).thenReturn("{\"k\":\"v\"}");
                    when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.from(Instant.parse("2026-05-16T00:00:00Z")));
                    return List.of(mapper.mapRow(resultSet, 0));
                });
        JdbcPgEventBusRepository repository = new JdbcPgEventBusRepository(namedParameterJdbcTemplate, jdbcTemplate, objectMapper);

        var rows = repository.pollEvents("consumer-a", 10);

        assertEquals(1, rows.size());
        assertEquals(7L, rows.get(0).id());
        assertEquals("source", rows.get(0).sourceSystem());
        assertEquals("target", rows.get(0).targetSystem());
        assertEquals("type", rows.get(0).eventType());
        assertEquals(Instant.parse("2026-05-16T00:00:00Z"), rows.get(0).createdAt());
    }

    @Test
    @SuppressWarnings("unchecked")
    void pollDeadLettersMapsTheResultSetIntoDeadLetterRows() throws Exception {
        when(namedParameterJdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(2);
                    ResultSet resultSet = org.mockito.Mockito.mock(ResultSet.class);
                    when(resultSet.getLong("dead_id")).thenReturn(9L);
                    when(resultSet.getObject("original_id", Long.class)).thenReturn(7L);
                    when(resultSet.getString("payload")).thenReturn("{\"k\":\"v\"}");
                    when(resultSet.getString("error_message")).thenReturn("boom");
                    when(resultSet.getTimestamp("failed_at")).thenReturn(Timestamp.from(Instant.parse("2026-05-16T00:03:00Z")));
                    return List.of(mapper.mapRow(resultSet, 0));
                });
        JdbcPgEventBusRepository repository = new JdbcPgEventBusRepository(namedParameterJdbcTemplate, jdbcTemplate, objectMapper);

        var rows = repository.pollDeadLetters("consumer-a", 10);

        assertEquals(1, rows.size());
        assertEquals(9L, rows.get(0).deadLetterId());
        assertEquals(7L, rows.get(0).originalEventId());
        assertEquals("boom", rows.get(0).errorMessage());
        assertEquals(Instant.parse("2026-05-16T00:03:00Z"), rows.get(0).failedAt());
    }
}
