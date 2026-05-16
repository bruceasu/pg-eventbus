package me.asu.pg_eventbus.gateway.api;

import me.asu.pg_eventbus.client.PgEventBusClient;
import me.asu.pg_eventbus.contract.ApiResponse;
import me.asu.pg_eventbus.contract.PublishResultDto;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/event-bus")
@ConditionalOnBean(PgEventBusClient.class)
public class EventController {

    private final PgEventBusClient client;

    public EventController(PgEventBusClient client) {
        this.client = client;
    }

    @PostMapping("/events")
    public ApiResponse<PublishResultDto> publish(@Valid @RequestBody PublishEventRequest request) {
        PublishResultDto result = client.publish(
                request.sourceSystem(),
                request.targetSystem(),
                request.eventType(),
                request.payload()
        );
        return ApiResponseFactory.ok(result, null, "event published");
    }
}