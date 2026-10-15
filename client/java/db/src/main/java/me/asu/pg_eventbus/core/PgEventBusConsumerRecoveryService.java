package me.asu.pg_eventbus.core;

import me.asu.pg_eventbus.contract.ConsumerDto;
import me.asu.pg_eventbus.contract.RecoverResultDto;
import me.asu.pg_eventbus.jdbc.PgEventBusRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@ConditionalOnProperty(prefix = "bus", name = "enabled", havingValue = "true")
@ConditionalOnBean(PgEventBusRepository.class)
public class PgEventBusConsumerRecoveryService {

    private final PgEventBusRepository repository;
    private final PgEventBusProperties properties;

    public PgEventBusConsumerRecoveryService(PgEventBusRepository repository, PgEventBusProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public RecoverResultDto registerAndInitializeAtTime(String consumerName, Instant startTime) {
        repository.registerConsumer(consumerName);
        long offset = repository.initConsumerAtTime(consumerName, startTime);
        ConsumerDto consumer = new ConsumerDto(
                consumerName,
                null,
                false,
                null,
                null,
                null,
                offset,
                offset,
                properties.batchSize()
        );
        return new RecoverResultDto(offset, consumer);
    }

    public boolean acquireLock(String consumerName, String instanceId, int ttlSeconds) {
        return repository.acquireConsumerLock(consumerName, instanceId, ttlSeconds);
    }

    public boolean renewLock(String consumerName, String instanceId, int ttlSeconds) {
        return repository.renewConsumerLock(consumerName, instanceId, ttlSeconds);
    }

    public boolean releaseLock(String consumerName, String instanceId) {
        return repository.releaseConsumerLock(consumerName, instanceId);
    }
}