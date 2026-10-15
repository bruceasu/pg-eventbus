package me.asu.pg_eventbus.cli.commands;

import me.asu.pg_eventbus.cli.CliDatabase;
import me.asu.pg_eventbus.cli.model.MaintenanceResultDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class PartitionCommand {

    private final CliDatabase client;

    public PartitionCommand(CliDatabase client) {
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