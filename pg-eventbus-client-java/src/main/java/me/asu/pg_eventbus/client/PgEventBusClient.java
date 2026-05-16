package me.asu.pg_eventbus.client;

import com.fasterxml.jackson.databind.JsonNode;
import me.asu.pg_eventbus.contract.CommitResultDto;
import me.asu.pg_eventbus.contract.MaintenanceResultDto;
import me.asu.pg_eventbus.contract.PollResultDto;
import me.asu.pg_eventbus.contract.PublishResultDto;
import me.asu.pg_eventbus.contract.RecoverResultDto;
import me.asu.pg_eventbus.core.PgEventBusConsumerCommitService;
import me.asu.pg_eventbus.core.PgEventBusConsumerPollService;
import me.asu.pg_eventbus.core.PgEventBusConsumerRecoveryService;
import me.asu.pg_eventbus.core.PgEventBusMaintenanceService;
import me.asu.pg_eventbus.core.PgEventBusPublishCommand;
import me.asu.pg_eventbus.core.PgEventBusPublisherService;

import java.time.Instant;

public final class PgEventBusClient {

    private final PgEventBusPublisherService publisherService;
    private final PgEventBusConsumerPollService pollService;
    private final PgEventBusConsumerRecoveryService recoveryService;
    private final PgEventBusConsumerCommitService commitService;
    private final PgEventBusMaintenanceService maintenanceService;

    public PgEventBusClient(
            PgEventBusPublisherService publisherService,
            PgEventBusConsumerPollService pollService,
            PgEventBusConsumerRecoveryService recoveryService,
            PgEventBusConsumerCommitService commitService,
            PgEventBusMaintenanceService maintenanceService
    ) {
        this.publisherService = publisherService;
        this.pollService = pollService;
        this.recoveryService = recoveryService;
        this.commitService = commitService;
        this.maintenanceService = maintenanceService;
    }

    public PublishResultDto publish(String sourceSystem, String targetSystem, String eventType, JsonNode payload) {
        return new PublishResultDto(publisherService.publish(new PgEventBusPublishCommand(
                sourceSystem,
                targetSystem,
                eventType,
                payload
        )));
    }

    public PollResultDto poll(String consumerName, String instanceId, Integer requestedLimit) {
        return pollService.poll(consumerName, instanceId, requestedLimit);
    }

    public CommitResultDto commit(String consumerName, String instanceId, long newLastId) {
        boolean committed = commitService.commit(consumerName, instanceId, newLastId);
        return new CommitResultDto(committed, newLastId);
    }

    public RecoverResultDto recover(String consumerName, Instant startTime) {
        return recoveryService.registerAndInitializeAtTime(consumerName, startTime);
    }

    public MaintenanceResultDto ensureCurrentPartition() {
        return maintenanceService.ensureCurrentPartition();
    }

    public MaintenanceResultDto ensureFuturePartitions() {
        return maintenanceService.ensureFuturePartitions();
    }

    public MaintenanceResultDto dropOldPartitions() {
        return maintenanceService.dropOldPartitions();
    }

    public MaintenanceResultDto dropOldPartitions(int keepMonths) {
        return maintenanceService.dropOldPartitions(keepMonths);
    }

    public MaintenanceResultDto runMaintenance() {
        return maintenanceService.runMaintenance();
    }

    public int cleanupConsumed() {
        return maintenanceService.cleanupConsumed();
    }
}