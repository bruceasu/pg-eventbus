package me.asu.pg_eventbus.cli.config;

import me.asu.pg_eventbus.client.PgEventBusClient;
import me.asu.pg_eventbus.core.PgEventBusConsumerCommitService;
import me.asu.pg_eventbus.core.PgEventBusConsumerPollService;
import me.asu.pg_eventbus.core.PgEventBusConsumerRecoveryService;
import me.asu.pg_eventbus.core.PgEventBusMaintenanceService;
import me.asu.pg_eventbus.core.PgEventBusPublisherService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "bus", name = "enabled", havingValue = "true")
public class PgEventBusClientConfiguration {

    @Bean
    @ConditionalOnBean({
            PgEventBusPublisherService.class,
            PgEventBusConsumerPollService.class,
            PgEventBusConsumerRecoveryService.class,
            PgEventBusConsumerCommitService.class,
            PgEventBusMaintenanceService.class
    })
    public PgEventBusClient pgEventBusClient(
            PgEventBusPublisherService publisherService,
            PgEventBusConsumerPollService pollService,
            PgEventBusConsumerRecoveryService recoveryService,
            PgEventBusConsumerCommitService commitService,
            PgEventBusMaintenanceService maintenanceService
    ) {
        return new PgEventBusClient(publisherService, pollService, recoveryService, commitService, maintenanceService);
    }
}