package me.asu.pg_eventbus.gateway.api;

import me.asu.pg_eventbus.client.PgEventBusClient;
import me.asu.pg_eventbus.contract.ApiResponse;
import me.asu.pg_eventbus.contract.CommitResultDto;
import me.asu.pg_eventbus.contract.PollResultDto;
import me.asu.pg_eventbus.contract.RecoverResultDto;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/event-bus/consumers")
@ConditionalOnBean(PgEventBusClient.class)
public class ConsumerController {

    private final PgEventBusClient client;

    public ConsumerController(PgEventBusClient client) {
        this.client = client;
    }

    @PostMapping("/{consumerName}/poll")
    public ApiResponse<PollResultDto> poll(
            @PathVariable String consumerName,
            @Valid @RequestBody ConsumerPollRequest request
    ) {
        PollResultDto result = client.poll(consumerName, request.instanceId(), request.limit());
        return ApiResponseFactory.ok(result, null, "poll completed");
    }

    @PostMapping("/{consumerName}/commit")
    public ApiResponse<CommitResultDto> commit(
            @PathVariable String consumerName,
            @Valid @RequestBody ConsumerCommitRequest request
    ) {
        return ApiResponseFactory.ok(client.commit(consumerName, request.instanceId(), request.newLastId()), null, "commit completed");
    }

    @PostMapping("/{consumerName}/recover")
    public ApiResponse<RecoverResultDto> recover(
            @PathVariable String consumerName,
            @Valid @RequestBody ConsumerRecoverRequest request
    ) {
        RecoverResultDto result = client.recover(consumerName, request.startTime());
        return ApiResponseFactory.ok(result, null, "consumer recovered");
    }
}