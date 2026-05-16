package me.asu.pg_eventbus.cli.commands;

import me.asu.pg_eventbus.client.PgEventBusClient;
import me.asu.pg_eventbus.contract.MaintenanceResultDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(PgEventBusClient.class)
public class PartitionCommand {

    private final PgEventBusClient client;

    public PartitionCommand(PgEventBusClient client) {
        this.client = client;
    }

    public MaintenanceResultDto ensureCurrent() {
        return client.ensureCurrentPartition();
    }

    public MaintenanceResultDto ensureFuture() {
        return client.ensureFuturePartitions();
    }

    public MaintenanceResultDto dropOlderThan(int keepMonths) {
        return client.dropOldPartitions(keepMonths);
    }
}