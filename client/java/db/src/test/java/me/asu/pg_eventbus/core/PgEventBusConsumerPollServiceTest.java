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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PgEventBusConsumerPollServiceTest {

    @Test
    void pollReturnsBatchWhenLockIsAcquired() {
        PgEventBusRepository repository = mock(PgEventBusRepository.class);
        PgEventBusProperties properties = new PgEventBusProperties(true, "consumer-a", 50, 30, 3, "bus");
        PgEventBusConsumerPollService service = new PgEventBusConsumerPollService(repository, properties);

        when(repository.acquireConsumerLock("consumer-a", "worker-1", 30)).thenReturn(true);
        when(repository.pollEvents("consumer-a", 50)).thenReturn(List.of(
                new EventRow(1L, "source-a", "consumer-a", "event.created", new ObjectMapper().createObjectNode(), Instant.parse("2026-05-15T00:00:00Z"))
        ));

        var result = service.poll("consumer-a", "worker-1", null);

        assertTrue(result.lockAcquired());
        assertEquals(50, result.batchSize());
        assertEquals(1, result.events().size());
        assertEquals(1L, result.events().get(0).id());
    }

    @Test
    void pollReturnsEmptyBatchWhenLockIsContended() {
        PgEventBusRepository repository = mock(PgEventBusRepository.class);
        PgEventBusProperties properties = new PgEventBusProperties(true, "consumer-a", 50, 30, 3, "bus");
        PgEventBusConsumerPollService service = new PgEventBusConsumerPollService(repository, properties);

        when(repository.acquireConsumerLock("consumer-a", "worker-1", 30)).thenReturn(false);

        var result = service.poll("consumer-a", "worker-1", null);

        assertFalse(result.lockAcquired());
        assertTrue(result.events().isEmpty());
        verify(repository, never()).pollEvents("consumer-a", 50);
    }
}
