package me.asu.pg_eventbus.client.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.asu.pg_eventbus.client.http.model.ApiResponse;
import me.asu.pg_eventbus.client.http.model.CommitResultDto;
import me.asu.pg_eventbus.client.http.model.ErrorDto;
import me.asu.pg_eventbus.client.http.model.MaintenanceResultDto;
import me.asu.pg_eventbus.client.http.model.PollResultDto;
import me.asu.pg_eventbus.client.http.model.PublishResultDto;
import me.asu.pg_eventbus.client.http.model.RecoverResultDto;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;

public final class PgEventBusHttpClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public PgEventBusHttpClient(String baseUrl) {
        this(baseUrl, HttpClient.newHttpClient(), new ObjectMapper().findAndRegisterModules());
    }

    public PgEventBusHttpClient(String baseUrl, HttpClient httpClient, ObjectMapper objectMapper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
    }

    public PublishResultDto publish(String sourceSystem, String targetSystem, String eventType, JsonNode payload) {
        ApiResponse<JsonNode> response = post("/api/event-bus/events", new PublishRequest(sourceSystem, targetSystem, eventType, payload));
        return new PublishResultDto(response.data().path("eventId").asLong());
    }

    public PollResultDto poll(String consumerName, String instanceId, Integer requestedLimit) {
        ApiResponse<JsonNode> response = post("/api/event-bus/consumers/" + consumerName + "/poll", new ConsumerPollRequest(instanceId, requestedLimit));
        return objectMapper.convertValue(response.data(), PollResultDto.class);
    }

    public CommitResultDto commit(String consumerName, String instanceId, long newLastId) {
        ApiResponse<JsonNode> response = post("/api/event-bus/consumers/" + consumerName + "/commit", new ConsumerCommitRequest(instanceId, newLastId));
        return objectMapper.convertValue(response.data(), CommitResultDto.class);
    }

    public RecoverResultDto recover(String consumerName, Instant startTime) {
        ApiResponse<JsonNode> response = post("/api/event-bus/consumers/" + consumerName + "/recover", new ConsumerRecoverRequest(startTime));
        return objectMapper.convertValue(response.data(), RecoverResultDto.class);
    }

    public MaintenanceResultDto runMaintenance() {
        return objectMapper.convertValue(post("/api/event-bus/maintenance/run", null).data(), MaintenanceResultDto.class);
    }

    public MaintenanceResultDto ensureCurrentPartition() {
        return objectMapper.convertValue(post("/api/event-bus/maintenance/ensure-current-partition", null).data(), MaintenanceResultDto.class);
    }

    public MaintenanceResultDto ensureFuturePartitions() {
        return objectMapper.convertValue(post("/api/event-bus/maintenance/ensure-future-partitions", null).data(), MaintenanceResultDto.class);
    }

    public MaintenanceResultDto dropOldPartitions(int keepMonths) {
        return objectMapper.convertValue(post("/api/event-bus/maintenance/drop-older-than/" + keepMonths, null).data(), MaintenanceResultDto.class);
    }

    public int cleanupConsumed() {
        ApiResponse<JsonNode> response = post("/api/event-bus/maintenance/cleanup-consumed", null);
        return response.data().asInt();
    }

    private ApiResponse<JsonNode> post(String path, Object body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .POST(body == null
                            ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Unexpected HTTP status " + response.statusCode() + ": " + response.body());
            }
            ApiResponse<JsonNode> envelope = objectMapper.readValue(
                    response.body(),
                    objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, JsonNode.class)
            );
            if (!envelope.success()) {
                ErrorDto error = envelope.error();
                if (error != null) {
                    throw new IllegalStateException(error.errorCode() + ": " + error.errorMessage());
                }
                throw new IllegalStateException(envelope.code() + ": " + envelope.message());
            }
            return envelope;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to call pg-eventbus gateway", e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to call pg-eventbus gateway", e);
        }
    }

    private record PublishRequest(String sourceSystem, String targetSystem, String eventType, JsonNode payload) {}
    private record ConsumerPollRequest(String instanceId, Integer limit) {}
    private record ConsumerCommitRequest(String instanceId, long newLastId) {}
    private record ConsumerRecoverRequest(Instant startTime) {}
}