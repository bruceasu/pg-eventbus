package me.asu.pg_eventbus.gateway.api;

import me.asu.pg_eventbus.gateway.GatewayService;
import me.asu.pg_eventbus.gateway.model.ApiResponse;
import me.asu.pg_eventbus.gateway.model.PublishResultDto;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/event-bus")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class EventController {

    private final GatewayService client;

    public EventController(GatewayService client) {
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