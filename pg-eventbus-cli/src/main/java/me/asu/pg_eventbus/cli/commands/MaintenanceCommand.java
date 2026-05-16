package me.asu.pg_eventbus.cli.commands;

import me.asu.pg_eventbus.client.PgEventBusClient;
import me.asu.pg_eventbus.contract.MaintenanceResultDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(PgEventBusClient.class)
public class MaintenanceCommand {

    private final PgEventBusClient client;

    public MaintenanceCommand(PgEventBusClient client) {
        this.client = client;
    }

    public MaintenanceResultDto run() {
        return client.runMaintenance();
    }
}