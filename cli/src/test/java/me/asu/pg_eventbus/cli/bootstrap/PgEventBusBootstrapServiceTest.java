package me.asu.pg_eventbus.cli.bootstrap;

import me.asu.pg_eventbus.cli.PgEventBusProperties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PgEventBusBootstrapServiceTest {

    @Test
    void initializeRewritesSchemaAndOwnerInInitScript() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        doNothing().when(jdbcTemplate).execute(anyString());
        PgEventBusProperties properties = new PgEventBusProperties();
        properties.setSchema("analytics");
        PgEventBusBootstrapService service = new PgEventBusBootstrapService(jdbcTemplate, properties);

        service.initialize("event_app");

        verify(jdbcTemplate, times(1)).execute(anyString());
        org.mockito.ArgumentCaptor<String> sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(1)).execute(sqlCaptor.capture());

        String joined = sqlCaptor.getValue();
        assertTrue(joined.contains("CREATE SCHEMA IF NOT EXISTS analytics;"));
        assertTrue(joined.contains("SET LOCAL search_path = analytics, pg_temp;"));
        assertTrue(joined.contains("GRANT USAGE ON SCHEMA %I TO event_app"));
        assertTrue(joined.contains("SET search_path FROM CURRENT"));
        assertFalse(joined.contains("analytics.register_consumer"));
        assertFalse(joined.contains("bus."));
        assertTrue(joined.contains("CREATE OR REPLACE FUNCTION register_consumer"));
    }
}
