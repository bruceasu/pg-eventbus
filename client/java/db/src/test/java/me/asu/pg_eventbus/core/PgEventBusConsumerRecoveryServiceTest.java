package me.asu.pg_eventbus.core;

import me.asu.pg_eventbus.jdbc.PgEventBusRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PgEventBusConsumerRecoveryServiceTest {

    @Test
    void registerAndInitializeAtTimeResetsTheStartingOffset() {
        var repository = mock(PgEventBusRepository.class);
        var properties = new PgEventBusProperties(true, "consumer-a", 50, 30, 3, "bus");
        var service = new PgEventBusConsumerRecoveryService(repository, properties);

        when(repository.initConsumerAtTime("consumer-a", Instant.parse("2026-05-15T00:00:00Z"))).thenReturn(123L);

        var result = service.registerAndInitializeAtTime("consumer-a", Instant.parse("2026-05-15T00:00:00Z"));

        assertEquals(123L, result.initializedOffset());
        assertEquals("consumer-a", result.consumer().consumerName());
        assertEquals(123L, result.consumer().currentOffset());
        verify(repository).registerConsumer("consumer-a");
        verify(repository).initConsumerAtTime("consumer-a", Instant.parse("2026-05-15T00:00:00Z"));
    }
}
