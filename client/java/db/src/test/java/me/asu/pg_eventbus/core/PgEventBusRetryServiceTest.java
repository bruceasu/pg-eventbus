package me.asu.pg_eventbus.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.asu.pg_eventbus.jdbc.PgEventBusRepository;
import me.asu.pg_eventbus.jdbc.model.RetryRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PgEventBusRetryServiceTest {

    @Test
    void processDueRetriesDeletesRetryOnSuccess() throws Exception {
        PgEventBusRepository repository = mock(PgEventBusRepository.class);
        PgEventBusProperties properties = new PgEventBusProperties(true, "consumer-a", 50, 30, 3, "bus");
        PgEventBusRetryService service = new PgEventBusRetryService(repository, properties);

        when(repository.acquireConsumerLock("consumer-a", "worker-1", 30)).thenReturn(true);
        when(repository.pollRetry("consumer-a", 50)).thenReturn(List.of(
                new RetryRow(100L, 10L, new ObjectMapper().createObjectNode(), 0)
        ));

        var result = service.processDueRetries("consumer-a", "worker-1", null, event -> {
        });

        assertTrue(result.lockAcquired());
        assertEquals(1, result.processedCount());
        assertEquals(0, result.retriedCount());
        assertEquals(0, result.deadletteredCount());
        verify(repository).deleteRetry(100L);
        verify(repository).releaseConsumerLock("consumer-a", "worker-1");
    }

    @Test
    void processDueRetriesMovesToDeadLetterAfterMaxRetries() throws Exception {
        PgEventBusRepository repository = mock(PgEventBusRepository.class);
        PgEventBusProperties properties = new PgEventBusProperties(true, "consumer-a", 50, 30, 3, "bus");
        PgEventBusRetryService service = new PgEventBusRetryService(repository, properties);

        when(repository.acquireConsumerLock("consumer-a", "worker-1", 30)).thenReturn(true);
        when(repository.pollRetry("consumer-a", 50)).thenReturn(List.of(
                new RetryRow(101L, 11L, new ObjectMapper().createObjectNode(), 2)
        ));

        var result = service.processDueRetries("consumer-a", "worker-1", null, event -> {
            throw new IllegalStateException("boom");
        });

        assertTrue(result.lockAcquired());
        assertEquals(1, result.processedCount());
        assertEquals(0, result.retriedCount());
        assertEquals(1, result.deadletteredCount());
        verify(repository).moveToDeadLetter(eq(11L), eq("consumer-a"), any(), eq("boom"));
        verify(repository).deleteRetry(101L);
        verify(repository).releaseConsumerLock("consumer-a", "worker-1");
    }
}
