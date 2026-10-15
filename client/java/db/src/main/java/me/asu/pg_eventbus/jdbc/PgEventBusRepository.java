package me.asu.pg_eventbus.jdbc;

import com.fasterxml.jackson.databind.JsonNode;
import me.asu.pg_eventbus.jdbc.model.DeadLetterRow;
import me.asu.pg_eventbus.jdbc.model.EventRow;
import me.asu.pg_eventbus.jdbc.model.RetryRow;

import java.time.Instant;
import java.util.List;

public interface PgEventBusRepository {

    long publishEvent(String sourceSystem, String targetSystem, String eventType, JsonNode payload);

    void registerConsumer(String consumerName);

    long initConsumerAtTime(String consumerName, Instant startTime);

    boolean acquireConsumerLock(String consumerName, String instanceId, int ttlSeconds);

    boolean renewConsumerLock(String consumerName, String instanceId, int ttlSeconds);

    boolean releaseConsumerLock(String consumerName, String instanceId);

    List<EventRow> pollEvents(String consumerName, int limit);

    boolean commitConsumerOffset(String consumerName, String instanceId, long newLastId);

    void scheduleRetry(long originalId, String consumerName, JsonNode payload, int retryCount);

    void moveToDeadLetter(long originalId, String consumerName, JsonNode payload, String errorMessage);

    List<RetryRow> pollRetry(String consumerName, int limit);

    void deleteRetry(long retryId);

    List<DeadLetterRow> pollDeadLetters(String consumerName, int limit);

    void ensureCurrentPartition();

    void ensureFuturePartitions(int monthsAhead);

    int cleanupConsumed(int batchSize);

    void dropPartitionOlderThan(int keepMonths);

    void maintenance();
}