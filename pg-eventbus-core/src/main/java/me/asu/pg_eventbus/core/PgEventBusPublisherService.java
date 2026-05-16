package me.asu.pg_eventbus.core;

import me.asu.pg_eventbus.jdbc.PgEventBusRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "bus", name = "enabled", havingValue = "true")
@ConditionalOnBean(PgEventBusRepository.class)
public class PgEventBusPublisherService {

    private final PgEventBusRepository repository;

    public PgEventBusPublisherService(PgEventBusRepository repository) {
        this.repository = repository;
    }

    public long publish(PgEventBusPublishCommand command) {
        return repository.publishEvent(
                command.sourceSystem(),
                command.targetSystem(),
                command.eventType(),
                command.payload()
        );
    }
}