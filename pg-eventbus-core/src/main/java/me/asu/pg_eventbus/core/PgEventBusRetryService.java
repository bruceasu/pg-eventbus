package me.asu.pg_eventbus.core;

import me.asu.pg_eventbus.contract.EventDto;
import me.asu.pg_eventbus.jdbc.PgEventBusRepository;
import me.asu.pg_eventbus.jdbc.model.RetryRow;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@ConditionalOnProperty(prefix = "bus", name = "enabled", havingValue = "true")
@ConditionalOnBean(PgEventBusRepository.class)
public class PgEventBusRetryService {

    private final PgEventBusRepository repository;
    private final PgEventBusProperties properties;

    public PgEventBusRetryService(PgEventBusRepository repository, PgEventBusProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional(transactionManager = "busTransactionManager")
    public PgEventBusRetryRunResult processDueRetries(
            String consumerName,
            String instanceId,
            Integer requestedLimit,
            PgEventBusEventHandler handler
    ) {
        int batchSize = requestedLimit == null || requestedLimit <= 0 ? properties.batchSize() : requestedLimit;
        boolean lockAcquired = repository.acquireConsumerLock(consumerName, instanceId, properties.lockTtlSeconds());
        if (!lockAcquired) {
            return new PgEventBusRetryRunResult(false, 0, 0, 0);
        }

        List<RetryRow> dueRetries = repository.pollRetry(consumerName, batchSize);
        int processedCount = 0;
        int retriedCount = 0;
        int deadletteredCount = 0;

        try {
            for (RetryRow retryItem : dueRetries) {
                processedCount++;
                EventDto retryEvent = new EventDto(
                        retryItem.originalId(),
                        "bus.retry",
                        consumerName,
                        "retry",
                        retryItem.payload(),
                        Instant.now()
                );
                try {
                    handler.handle(retryEvent);
                    repository.deleteRetry(retryItem.retryId());
                } catch (Exception ex) {
                    if (retryItem.retryCount() + 1 >= properties.maxRetries()) {
                        repository.moveToDeadLetter(
                                retryItem.originalId(),
                                consumerName,
                                retryItem.payload(),
                                ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()
                        );
                        repository.deleteRetry(retryItem.retryId());
                        deadletteredCount++;
                    } else {
                        repository.scheduleRetry(
                                retryItem.originalId(),
                                consumerName,
                                retryItem.payload(),
                                retryItem.retryCount()
                        );
                        repository.deleteRetry(retryItem.retryId());
                        retriedCount++;
                    }
                }
            }
        } finally {
            repository.releaseConsumerLock(consumerName, instanceId);
        }

        return new PgEventBusRetryRunResult(true, processedCount, retriedCount, deadletteredCount);
    }
}