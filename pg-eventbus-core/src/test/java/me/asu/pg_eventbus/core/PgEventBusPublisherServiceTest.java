package me.asu.pg_eventbus.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.asu.pg_eventbus.jdbc.PgEventBusRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PgEventBusPublisherServiceTest {

    @Test
    void publishDelegatesToRepository() {
        PgEventBusRepository repository = mock(PgEventBusRepository.class);
        PgEventBusPublisherService service = new PgEventBusPublisherService(repository);
        ObjectMapper objectMapper = new ObjectMapper();

        when(repository.publishEvent("source-a", "consumer-a", "event.created", objectMapper.createObjectNode()))
                .thenReturn(42L);

        long eventId = service.publish(new PgEventBusPublishCommand(
                "source-a",
                "consumer-a",
                "event.created",
                objectMapper.createObjectNode()
        ));

        assertEquals(42L, eventId);
    }
}