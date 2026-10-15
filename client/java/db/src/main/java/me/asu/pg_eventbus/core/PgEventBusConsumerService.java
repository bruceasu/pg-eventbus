package me.asu.pg_eventbus.core;

import me.asu.pg_eventbus.contract.EventDto;
import me.asu.pg_eventbus.jdbc.PgEventBusRepository;
import me.asu.pg_eventbus.jdbc.model.EventRow;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@ConditionalOnProperty(prefix = "bus", name = "enabled", havingValue = "true")
@ConditionalOnBean(PgEventBusRepository.class)
public class PgEventBusConsumerService {

    private final PgEventBusRepository repository;
    private final PgEventBusProperties properties;

    public PgEventBusConsumerService(PgEventBusRepository repository, PgEventBusProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional(transactionManager = "busTransactionManager")
    public PgEventBusRunResult pollAndHandle(
            String consumerName,
            String instanceId,
            Integer requestedLimit,
            PgEventBusEventHandler handler
    ) {
        int batchSize = requestedLimit == null || requestedLimit <= 0 ? properties.batchSize() : requestedLimit;
        if (!repository.acquireConsumerLock(consumerName, instanceId, properties.lockTtlSeconds())) {
            return new PgEventBusRunResult(false, 0, 0L, false);
        }

        List<EventRow> rows = repository.pollEvents(consumerName, batchSize);
        EventDto failedEvent = null;
        long lastSuccessfulId = 0L;
        int processedCount = 0;
        boolean retryScheduled = false;

        try {
            for (EventRow row : rows) {
                EventDto event = toEventDto(row);
                handler.handle(event);
                lastSuccessfulId = event.id();
                processedCount++;
            }
        } catch (Exception ex) {
            failedEvent = lastSuccessfulId == 0L && !rows.isEmpty() ? toEventDto(rows.get(0)) : findNextEvent(rows, lastSuccessfulId);
            if (failedEvent != null) {
                repository.scheduleRetry(failedEvent.id(), consumerName, failedEvent.payload(), 0);
                retryScheduled = true;
            }
        } finally {
            if (lastSuccessfulId > 0L) {
                repository.commitConsumerOffset(consumerName, instanceId, lastSuccessfulId);
            }
            repository.releaseConsumerLock(consumerName, instanceId);
        }

        return new PgEventBusRunResult(true, processedCount, lastSuccessfulId, retryScheduled);
    }

    private EventDto findNextEvent(List<EventRow> rows, long lastSuccessfulId) {
        for (EventRow row : rows) {
            if (row.id() > lastSuccessfulId) {
                return toEventDto(row);
            }
        }
        return null;
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