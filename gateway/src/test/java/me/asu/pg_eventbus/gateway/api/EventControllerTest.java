package me.asu.pg_eventbus.gateway.api;

import me.asu.pg_eventbus.gateway.GatewayService;
import me.asu.pg_eventbus.gateway.model.PublishResultDto;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EventControllerTest {
    @Test
    void existingRoutePreservesJsonContract() throws Exception {
        GatewayService service = mock(GatewayService.class);
        when(service.publish(eq("source"), eq("target"), eq("created"), any())).thenReturn(new PublishResultDto(42));
        var http = MockMvcBuilders.standaloneSetup(new EventController(service)).build();
        http.perform(post("/api/event-bus/events").contentType("application/json")
                .content("{\"sourceSystem\":\"source\",\"targetSystem\":\"target\",\"eventType\":\"created\",\"payload\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.eventId").value(42));
    }
}
