package me.asu.pg_eventbus.client.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.asu.pg_eventbus.client.http.model.PublishResultDto;
import me.asu.pg_eventbus.client.http.model.PollResultDto;
import me.asu.pg_eventbus.client.http.model.CommitResultDto;
import me.asu.pg_eventbus.client.http.model.RecoverResultDto;
import me.asu.pg_eventbus.client.http.model.MaintenanceResultDto;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Instant;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PgEventBusHttpClientTest {

    private static HttpServer server;
    private static int port;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.createContext("/api/event-bus/events", exchange -> write(exchange, publishResponse()));
        server.createContext("/api/event-bus/consumers/consumer-a/poll", exchange -> write(exchange, pollResponse()));
        server.createContext("/api/event-bus/consumers/consumer-a/commit", exchange -> write(exchange, commitResponse()));
        server.createContext("/api/event-bus/consumers/consumer-a/recover", exchange -> write(exchange, recoverResponse()));
        server.createContext("/api/event-bus/maintenance/run", exchange -> write(exchange, maintenanceResponse("maintenance")));
        server.createContext("/api/event-bus/maintenance/ensure-current-partition", exchange -> write(exchange, maintenanceResponse("ensure-current")));
        server.createContext("/api/event-bus/maintenance/ensure-future-partitions", exchange -> write(exchange, maintenanceResponse("ensure-future")));
        server.createContext("/api/event-bus/maintenance/drop-older-than/3", exchange -> write(exchange, maintenanceResponse("drop-old")));
        server.createContext("/api/event-bus/maintenance/cleanup-consumed", exchange -> write(exchange, cleanupResponse()));
        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @Test
    void exposesFlatApi() {
        PgEventBusHttpClient client = new PgEventBusHttpClient("http://localhost:" + port);
        PublishResultDto published = client.publish("source", "target", "event", new ObjectMapper().createObjectNode());
        PollResultDto polled = client.poll("consumer-a", "instance-a", 5);
        CommitResultDto committed = client.commit("consumer-a", "instance-a", 7L);
        RecoverResultDto recovered = client.recover("consumer-a", Instant.parse("2026-05-16T00:00:00Z"));
        MaintenanceResultDto maintenance = client.runMaintenance();

        assertEquals(11L, published.eventId());
        assertTrue(polled.lockAcquired());
        assertTrue(committed.committed());
        assertEquals(7L, recovered.initializedOffset());
        assertTrue(maintenance.executed());
        assertEquals(17, client.cleanupConsumed());
    }

    private static String publishResponse() {
        return """
                {"success":true,"code":"OK","message":"event published","requestId":"","timestamp":"2026-05-16T00:00:00Z","data":{"eventId":11},"error":null}
                """;
    }

    private static String pollResponse() {
        return """
                {"success":true,"code":"OK","message":"poll completed","requestId":"","timestamp":"2026-05-16T00:00:00Z","data":{"consumer":null,"events":[],"batchSize":5,"lockAcquired":true,"polledAt":"2026-05-16T00:00:00Z"},"error":null}
                """;
    }

    private static String commitResponse() {
        return """
                {"success":true,"code":"OK","message":"commit completed","requestId":"","timestamp":"2026-05-16T00:00:00Z","data":{"committed":true,"newLastCommittedEventId":7},"error":null}
                """;
    }

    private static String recoverResponse() {
        return """
                {"success":true,"code":"OK","message":"consumer recovered","requestId":"","timestamp":"2026-05-16T00:00:00Z","data":{"initializedOffset":7,"consumer":null},"error":null}
                """;
    }

    private static String maintenanceResponse(String action) {
        return """
                {"success":true,"code":"OK","message":"ok","requestId":"","timestamp":"2026-05-16T00:00:00Z","data":{"executed":true,"actions":["%s"],"completedAt":"2026-05-16T00:00:00Z"},"error":null}
                """.formatted(action);
    }

    private static String cleanupResponse() {
        return """
                {"success":true,"code":"OK","message":"ok","requestId":"","timestamp":"2026-05-16T00:00:00Z","data":17,"error":null}
                """;
    }

    private static void write(HttpExchange exchange, String payload) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        byte[] bytes = payload.getBytes();
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}