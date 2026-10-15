package me.asu.pg_eventbus.gateway.api;

import me.asu.pg_eventbus.gateway.GatewayService;
import me.asu.pg_eventbus.gateway.model.ApiResponse;
import me.asu.pg_eventbus.gateway.model.MaintenanceResultDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/event-bus/maintenance")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class MaintenanceController {

    private final GatewayService client;

    public MaintenanceController(GatewayService client) {
        this.client = client;
    }

    @PostMapping("/run")
    public ApiResponse<MaintenanceResultDto> run() {
        return ApiResponseFactory.ok(client.runMaintenance(), null, "maintenance completed");
    }

    @PostMapping("/ensure-current-partition")
    public ApiResponse<MaintenanceResultDto> ensureCurrentPartition() {
        return ApiResponseFactory.ok(client.ensureCurrentPartition(), null, "current partition ensured");
    }

    @PostMapping("/ensure-future-partitions")
    public ApiResponse<MaintenanceResultDto> ensureFuturePartitions() {
        return ApiResponseFactory.ok(client.ensureFuturePartitions(), null, "future partitions ensured");
    }

    @PostMapping("/drop-older-than/{keepMonths}")
    public ApiResponse<MaintenanceResultDto> dropOlderThan(@org.springframework.web.bind.annotation.PathVariable int keepMonths) {
        return ApiResponseFactory.ok(client.dropOldPartitions(keepMonths), null, "old partitions dropped");
    }

    @PostMapping("/cleanup-consumed")
    public ApiResponse<Integer> cleanupConsumed() {
        return ApiResponseFactory.ok(client.cleanupConsumed(), null, "consumed rows cleaned");
    }
}