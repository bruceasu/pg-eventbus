package me.asu.pg_eventbus.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.asu.pg_eventbus.contract.PollResultDto;
import me.asu.pg_eventbus.core.PgEventBusConsumerCommitService;
import me.asu.pg_eventbus.core.PgEventBusConsumerPollService;
import me.asu.pg_eventbus.core.PgEventBusConsumerRecoveryService;
import me.asu.pg_eventbus.core.PgEventBusMaintenanceService;
import me.asu.pg_eventbus.core.PgEventBusPublisherService;
import me.asu.pg_eventbus.contract.CommitResultDto;
import me.asu.pg_eventbus.contract.MaintenanceResultDto;
import me.asu.pg_eventbus.contract.PublishResultDto;
import me.asu.pg_eventbus.contract.RecoverResultDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PgEventBusClientTest {

    @Test
    void exposesFlatOperations() {
        PgEventBusPublisherService publisherService = mock(PgEventBusPublisherService.class);
        PgEventBusConsumerPollService pollService = mock(PgEventBusConsumerPollService.class);
        PgEventBusConsumerRecoveryService recoveryService = mock(PgEventBusConsumerRecoveryService.class);
        PgEventBusConsumerCommitService commitService = mock(PgEventBusConsumerCommitService.class);
        PgEventBusMaintenanceService maintenanceService = mock(PgEventBusMaintenanceService.class);
        PgEventBusClient client = new PgEventBusClient(
                publisherService,
                pollService,
                recoveryService,
                commitService,
                maintenanceService
        );

        when(publisherService.publish(org.mockito.ArgumentMatchers.any())).thenReturn(11L);
        when(pollService.poll("consumer-a", "instance-a", 5)).thenReturn(new PollResultDto(null, java.util.List.of(), 5, true, java.time.Instant.EPOCH));
        when(commitService.commit("consumer-a", "instance-a", 7L)).thenReturn(true);
        when(recoveryService.registerAndInitializeAtTime("consumer-a", java.time.Instant.parse("2026-05-16T00:00:00Z")))
                .thenReturn(new RecoverResultDto(7L, null));
        when(maintenanceService.ensureCurrentPartition()).thenReturn(new MaintenanceResultDto(true, java.util.List.of("ok"), java.time.Instant.EPOCH));

        PublishResultDto published = client.publish("source", "target", "event", new ObjectMapper().createObjectNode());
        PollResultDto polled = client.poll("consumer-a", "instance-a", 5);
        CommitResultDto committed = client.commit("consumer-a", "instance-a", 7L);
        RecoverResultDto recovered = client.recover("consumer-a", java.time.Instant.parse("2026-05-16T00:00:00Z"));
        MaintenanceResultDto maintenance = client.ensureCurrentPartition();

        assertEquals(11L, published.eventId());
        assertTrue(polled.lockAcquired());
        assertTrue(committed.committed());
        assertEquals(7L, recovered.initializedOffset());
        assertEquals(maintenance, client.ensureCurrentPartition());
    }
}