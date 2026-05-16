package me.asu.pg_eventbus.core;

import me.asu.pg_eventbus.jdbc.PgEventBusRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "bus", name = "enabled", havingValue = "true")
@ConditionalOnBean(PgEventBusRepository.class)
public class PgEventBusConsumerCommitService {

    private final PgEventBusRepository repository;

    public PgEventBusConsumerCommitService(PgEventBusRepository repository) {
        this.repository = repository;
    }

    public boolean commit(String consumerName, String instanceId, long newLastId) {
        return repository.commitConsumerOffset(consumerName, instanceId, newLastId);
    }
}