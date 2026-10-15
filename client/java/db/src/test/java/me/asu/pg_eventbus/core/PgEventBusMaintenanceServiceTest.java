package me.asu.pg_eventbus.core;

import me.asu.pg_eventbus.jdbc.PgEventBusRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PgEventBusMaintenanceServiceTest {

    @Test
    void runMaintenanceDelegatesToRepository() {
        var repository = mock(PgEventBusRepository.class);
        var properties = new PgEventBusProperties(true, "consumer-a", 50, 30, 3, "bus");
        var service = new PgEventBusMaintenanceService(repository, properties);

        var result = service.runMaintenance();

        assertTrue(result.executed());
        assertEquals(1, result.actions().size());
        verify(repository).maintenance();
    }

    @Test
    void ensureFuturePartitionsUsesDefaultHorizon() {
        var repository = mock(PgEventBusRepository.class);
        var properties = new PgEventBusProperties(true, "consumer-a", 50, 30, 3, "bus");
        var service = new PgEventBusMaintenanceService(repository, properties);

        service.ensureFuturePartitions();

        verify(repository).ensureFuturePartitions(6);
    }

    @Test
    void ensureCurrentPartitionDelegatesToRepository() {
        var repository = mock(PgEventBusRepository.class);
        var properties = new PgEventBusProperties(true, "consumer-a", 50, 30, 3, "bus");
        var service = new PgEventBusMaintenanceService(repository, properties);

        service.ensureCurrentPartition();

        verify(repository).ensureCurrentPartition();
    }

    @Test
    void dropOldPartitionsUsesRetentionWindow() {
        var repository = mock(PgEventBusRepository.class);
        var properties = new PgEventBusProperties(true, "consumer-a", 50, 30, 3, "bus");
        var service = new PgEventBusMaintenanceService(repository, properties);

        service.dropOldPartitions();

        verify(repository).dropPartitionOlderThan(3);
    }

    @Test
    void cleanupConsumedReturnsRepositoryCount() {
        var repository = mock(PgEventBusRepository.class);
        var properties = new PgEventBusProperties(true, "consumer-a", 50, 30, 3, "bus");
        var service = new PgEventBusMaintenanceService(repository, properties);

        when(repository.cleanupConsumed(10_000)).thenReturn(27);

        int removed = service.cleanupConsumed();

        assertEquals(27, removed);
        verify(repository).cleanupConsumed(10_000);
    }
}
