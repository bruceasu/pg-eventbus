package me.asu.pg_eventbus.core;

import me.asu.pg_eventbus.contract.MaintenanceResultDto;
import me.asu.pg_eventbus.jdbc.PgEventBusRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@ConditionalOnProperty(prefix = "bus", name = "enabled", havingValue = "true")
@ConditionalOnBean(PgEventBusRepository.class)
public class PgEventBusMaintenanceService {

    private final PgEventBusRepository repository;
    private final PgEventBusProperties properties;

    public PgEventBusMaintenanceService(PgEventBusRepository repository, PgEventBusProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public MaintenanceResultDto ensureCurrentPartition() {
        repository.ensureCurrentPartition();
        return new MaintenanceResultDto(true, List.of("ensure_current_partition"), Instant.now());
    }

    public MaintenanceResultDto ensureFuturePartitions() {
        repository.ensureFuturePartitions(6);
        return new MaintenanceResultDto(true, List.of("ensure_future_partitions:6"), Instant.now());
    }

    public int cleanupConsumed() {
        return repository.cleanupConsumed(10_000);
    }

    public MaintenanceResultDto dropOldPartitions() {
        return dropOldPartitions(3);
    }

    public MaintenanceResultDto dropOldPartitions(int keepMonths) {
        repository.dropPartitionOlderThan(keepMonths);
        return new MaintenanceResultDto(true, List.of("drop_partition_older_than:" + keepMonths), Instant.now());
    }

    public MaintenanceResultDto runMaintenance() {
        repository.maintenance();
        return new MaintenanceResultDto(true, List.of("maintenance"), Instant.now());
    }

    public int batchSize() {
        return properties.batchSize();
    }
}