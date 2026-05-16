package me.asu.pg_eventbus.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ContractDtoTest {

    @Test
    void apiResponseExposesTheExpectedRecordComponents() {
        var components = ApiResponse.class.getRecordComponents();

        assertEquals(7, components.length);
        assertEquals("success", components[0].getName());
        assertEquals("code", components[1].getName());
        assertEquals("message", components[2].getName());
        assertEquals("requestId", components[3].getName());
        assertEquals("timestamp", components[4].getName());
        assertEquals("data", components[5].getName());
        assertEquals("error", components[6].getName());
    }

    @Test
    void eventDtoPreservesPayloadAndTimestampShape() {
        JsonNode payload = JsonNodeFactory.instance.objectNode().put("k", "v");
        EventDto dto = new EventDto(7L, "source", "target", "type", payload, Instant.parse("2026-05-16T00:00:00Z"));

        assertEquals(7L, dto.id());
        assertEquals("source", dto.sourceSystem());
        assertEquals("target", dto.targetSystem());
        assertEquals("type", dto.eventType());
        assertEquals(payload, dto.payload());
        assertEquals(Instant.parse("2026-05-16T00:00:00Z"), dto.createdAt());
    }

    @Test
    void consumerAndErrorDtosCanBeComposed() {
        ConsumerDto consumer = new ConsumerDto(
                "consumer-a",
                "instance-1",
                true,
                "worker-1",
                Instant.parse("2026-05-16T00:01:00Z"),
                Instant.parse("2026-05-16T00:06:00Z"),
                41L,
                42L,
                100
        );
        ErrorDto error = new ErrorDto(
                "VALIDATION_ERROR",
                "bad payload",
                List.of("payload missing required field"),
                List.of(new FieldErrorDto("payload", "{}", "required field missing"))
        );
        ApiResponse<ConsumerDto> response = new ApiResponse<>(
                true,
                "OK",
                "ok",
                "req-1",
                Instant.parse("2026-05-16T00:02:00Z"),
                consumer,
                error
        );

        assertInstanceOf(ConsumerDto.class, response.data());
        assertEquals("VALIDATION_ERROR", response.error().errorCode());
        assertEquals("consumer-a", response.data().consumerName());
    }
}