package me.asu.pg_eventbus.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.asu.pg_eventbus.client.PgEventBusClient;
import me.asu.pg_eventbus.contract.CommitResultDto;
import me.asu.pg_eventbus.contract.PollResultDto;
import me.asu.pg_eventbus.contract.PublishResultDto;
import me.asu.pg_eventbus.contract.RecoverResultDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@ConditionalOnBean(PgEventBusClient.class)
public class EventCommand {

    private final PgEventBusClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EventCommand(PgEventBusClient client) {
        this.client = client;
    }

    public PublishResultDto publish(String sourceSystem, String targetSystem, String eventType, String payloadJson) throws Exception {
        return client.publish(
                sourceSystem,
                targetSystem,
                eventType,
                objectMapper.readTree(payloadJson)
        );
    }

    public PollResultDto poll(String consumerName, String instanceId, Integer limit) {
        return client.poll(consumerName, instanceId, limit);
    }

    public CommitResultDto commit(String consumerName, String instanceId, long newLastId) {
        return client.commit(consumerName, instanceId, newLastId);
    }

    public RecoverResultDto recover(String consumerName, Instant startTime) {
        return client.recover(consumerName, startTime);
    }
}