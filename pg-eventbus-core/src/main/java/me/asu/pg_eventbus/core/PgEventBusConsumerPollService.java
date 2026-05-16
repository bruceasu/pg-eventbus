package me.asu.pg_eventbus.core;

import me.asu.pg_eventbus.contract.ConsumerDto;
import me.asu.pg_eventbus.contract.EventDto;
import me.asu.pg_eventbus.contract.PollResultDto;
import me.asu.pg_eventbus.jdbc.PgEventBusRepository;
import me.asu.pg_eventbus.jdbc.model.EventRow;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@ConditionalOnProperty(prefix = "bus", name = "enabled", havingValue = "true")
@ConditionalOnBean(PgEventBusRepository.class)
public class PgEventBusConsumerPollService {

    private final PgEventBusRepository repository;
    private final PgEventBusProperties properties;

    public PgEventBusConsumerPollService(PgEventBusRepository repository, PgEventBusProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public PollResultDto poll(String consumerName, String instanceId, Integer requestedLimit) {
        int batchSize = requestedLimit == null || requestedLimit <= 0 ? properties.batchSize() : requestedLimit;
        boolean lockAcquired = repository.acquireConsumerLock(consumerName, instanceId, properties.lockTtlSeconds());
        ConsumerDto consumer = new ConsumerDto(
                consumerName,
                instanceId,
                lockAcquired,
                lockAcquired ? instanceId : null,
                null,
                null,
                0L,
                0L,
                batchSize
        );
        if (!lockAcquired) {
            return new PollResultDto(consumer, List.of(), batchSize, false, Instant.now());
        }

        List<EventDto> events = repository.pollEvents(consumerName, batchSize)
                .stream()
                .map(this::toEventDto)
                .toList();
        return new PollResultDto(consumer, events, batchSize, true, Instant.now());
    }

    private EventDto toEventDto(EventRow row) {
        return new EventDto(
                row.id(),
                row.sourceSystem(),
                row.targetSystem(),
                row.eventType(),
                row.payload(),
                row.createdAt()
        );
    }
}