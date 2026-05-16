package me.asu.pg_eventbus.cli.bootstrap;

import me.asu.pg_eventbus.core.PgEventBusProperties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PgEventBusBootstrapServiceTest {

    @Test
    void initializeRewritesSchemaAndOwnerAcrossBootstrapScripts() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        doNothing().when(jdbcTemplate).execute(anyString());
        PgEventBusProperties properties = new PgEventBusProperties(true, "consumer-a", 50, 30, 3, "analytics");
        PgEventBusBootstrapService service = new PgEventBusBootstrapService(jdbcTemplate, properties);

        service.initialize("app_role");

        verify(jdbcTemplate, times(24)).execute(anyString());
        org.mockito.ArgumentCaptor<String> sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(24)).execute(sqlCaptor.capture());

        String joined = String.join("\n", sqlCaptor.getAllValues());
        assertTrue(joined.contains("CREATE SCHEMA IF NOT EXISTS analytics;"));
        assertTrue(joined.contains("GRANT USAGE ON SCHEMA analytics TO app_role;"));
        assertTrue(joined.contains("SELECT \"analytics\".\"ensure_future_partitions\"(6);"));
    }
}
