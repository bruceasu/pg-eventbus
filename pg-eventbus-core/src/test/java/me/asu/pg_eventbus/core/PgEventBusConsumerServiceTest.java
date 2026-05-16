package me.asu.pg_eventbus.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.asu.pg_eventbus.jdbc.PgEventBusRepository;
import me.asu.pg_eventbus.jdbc.model.EventRow;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PgEventBusConsumerServiceTest {

    @Test
    void pollAndHandleCommitsLastSuccessfulOffset() throws Exception {
        PgEventBusRepository repository = mock(PgEventBusRepository.class);
        PgEventBusProperties properties = new PgEventBusProperties(true, "consumer-a", 100, 30, 3);
        PgEventBusConsumerService service = new PgEventBusConsumerService(repository, properties);
        ObjectMapper objectMapper = new ObjectMapper();

        when(repository.acquireConsumerLock("consumer-a", "worker-1", 30)).thenReturn(true);
        when(repository.pollEvents("consumer-a", 100)).thenReturn(List.of(
                new EventRow(10L, "source-a", "consumer-a", "event.created", objectMapper.createObjectNode(), Instant.parse("2026-05-15T00:00:00Z")),
                new EventRow(11L, "source-a", "consumer-a", "event.updated", objectMapper.createObjectNode(), Instant.parse("2026-05-15T00:01:00Z"))
        ));

        var result = service.pollAndHandle("consumer-a", "worker-1", null, event -> {
        });

        assertTrue(result.lockAcquired());
        assertEquals(2, result.processedCount());
        assertEquals(11L, result.committedOffset());
        verify(repository).commitConsumerOffset("consumer-a", "worker-1", 11L);
        verify(repository).releaseConsumerLock("consumer-a", "worker-1");
    }

    @Test
    void pollAndHandleSchedulesRetryWhenHandlerFails() throws Exception {
        PgEventBusRepository repository = mock(PgEventBusRepository.class);
        PgEventBusProperties properties = new PgEventBusProperties(true, "consumer-a", 100, 30, 3);
        PgEventBusConsumerService service = new PgEventBusConsumerService(repository, properties);
        ObjectMapper objectMapper = new ObjectMapper();

        when(repository.acquireConsumerLock("consumer-a", "worker-1", 30)).thenReturn(true);
        when(repository.pollEvents("consumer-a", 100)).thenReturn(List.of(
                new EventRow(10L, "source-a", "consumer-a", "event.created", objectMapper.createObjectNode(), Instant.parse("2026-05-15T00:00:00Z")),
                new EventRow(11L, "source-a", "consumer-a", "event.updated", objectMapper.createObjectNode(), Instant.parse("2026-05-15T00:01:00Z"))
        ));

        var result = service.pollAndHandle("consumer-a", "worker-1", null, event -> {
            if (event.id() == 11L) {
                throw new IllegalStateException("boom");
            }
        });

        assertTrue(result.lockAcquired());
        assertEquals(1, result.processedCount());
        assertEquals(10L, result.committedOffset());
        assertTrue(result.retryScheduled());
        verify(repository).scheduleRetry(11L, "consumer-a", objectMapper.createObjectNode(), 0);
        verify(repository).commitConsumerOffset("consumer-a", "worker-1", 10L);
        verify(repository).releaseConsumerLock("consumer-a", "worker-1");
    }
}