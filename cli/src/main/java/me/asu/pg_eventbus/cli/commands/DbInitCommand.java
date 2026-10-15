package me.asu.pg_eventbus.cli.commands;

import me.asu.pg_eventbus.cli.bootstrap.PgEventBusBootstrapService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class DbInitCommand {

    private final PgEventBusBootstrapService bootstrapService;

    public DbInitCommand(PgEventBusBootstrapService bootstrapService) {
        this.bootstrapService = bootstrapService;
    }

    public void initialize(String owner) {
        bootstrapService.initialize(owner);
    }
}
