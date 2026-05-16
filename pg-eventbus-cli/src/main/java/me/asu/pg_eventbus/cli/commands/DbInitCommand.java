package me.asu.pg_eventbus.cli.commands;

import me.asu.pg_eventbus.cli.bootstrap.PgEventBusBootstrapService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(PgEventBusBootstrapService.class)
public class DbInitCommand {

    private final PgEventBusBootstrapService bootstrapService;

    public DbInitCommand(PgEventBusBootstrapService bootstrapService) {
        this.bootstrapService = bootstrapService;
    }

    public void initialize(String owner) {
        bootstrapService.initialize(owner);
    }
}
