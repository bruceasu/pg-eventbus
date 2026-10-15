package me.asu.pg_eventbus.cli.commands;

import me.asu.pg_eventbus.cli.CliDatabase;
import me.asu.pg_eventbus.cli.model.MaintenanceResultDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class MaintenanceCommand {

    private final CliDatabase client;

    public MaintenanceCommand(CliDatabase client) {
        this.client = client;
    }

    public MaintenanceResultDto run() {
        return client.runMaintenance();
    }
}