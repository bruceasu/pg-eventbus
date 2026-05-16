package pgeventbus

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestClientFlatAPI(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		switch r.URL.Path {
		case "/api/event-bus/events":
			_, _ = w.Write([]byte(`{"success":true,"code":"OK","message":"event published","requestId":"","timestamp":"2026-05-16T00:00:00Z","data":{"eventId":11},"error":null}`))
		case "/api/event-bus/consumers/consumer-a/poll":
			_, _ = w.Write([]byte(`{"success":true,"code":"OK","message":"poll completed","requestId":"","timestamp":"2026-05-16T00:00:00Z","data":{"consumer":null,"events":[],"batchSize":5,"lockAcquired":true,"polledAt":"2026-05-16T00:00:00Z"},"error":null}`))
		case "/api/event-bus/consumers/consumer-a/commit":
			_, _ = w.Write([]byte(`{"success":true,"code":"OK","message":"commit completed","requestId":"","timestamp":"2026-05-16T00:00:00Z","data":{"committed":true,"newLastCommittedEventId":7},"error":null}`))
		case "/api/event-bus/consumers/consumer-a/recover":
			_, _ = w.Write([]byte(`{"success":true,"code":"OK","message":"consumer recovered","requestId":"","timestamp":"2026-05-16T00:00:00Z","data":{"initializedOffset":7,"consumer":null},"error":null}`))
		case "/api/event-bus/maintenance/run":
			_, _ = w.Write([]byte(`{"success":true,"code":"OK","message":"maintenance completed","requestId":"","timestamp":"2026-05-16T00:00:00Z","data":{"executed":true,"actions":["maintenance"],"completedAt":"2026-05-16T00:00:00Z"},"error":null}`))
		case "/api/event-bus/maintenance/ensure-current-partition":
			_, _ = w.Write([]byte(`{"success":true,"code":"OK","message":"current partition ensured","requestId":"","timestamp":"2026-05-16T00:00:00Z","data":{"executed":true,"actions":["ensure-current"],"completedAt":"2026-05-16T00:00:00Z"},"error":null}`))
		case "/api/event-bus/maintenance/ensure-future-partitions":
			_, _ = w.Write([]byte(`{"success":true,"code":"OK","message":"future partitions ensured","requestId":"","timestamp":"2026-05-16T00:00:00Z","data":{"executed":true,"actions":["ensure-future"],"completedAt":"2026-05-16T00:00:00Z"},"error":null}`))
		case "/api/event-bus/maintenance/drop-older-than/3":
			_, _ = w.Write([]byte(`{"success":true,"code":"OK","message":"old partitions dropped","requestId":"","timestamp":"2026-05-16T00:00:00Z","data":{"executed":true,"actions":["drop-old"],"completedAt":"2026-05-16T00:00:00Z"},"error":null}`))
		case "/api/event-bus/maintenance/cleanup-consumed":
			_, _ = w.Write([]byte(`{"success":true,"code":"OK","message":"consumed rows cleaned","requestId":"","timestamp":"2026-05-16T00:00:00Z","data":17,"error":null}`))
		default:
			http.NotFound(w, r)
		}
	}))
	defer server.Close()

	client := NewWithHTTPClient(server.URL, server.Client())
	ctx := context.Background()

	published, err := client.Publish(ctx, "source", "target", "event", json.RawMessage(`{"hello":"world"}`))
	if err != nil || published.EventID != 11 {
		t.Fatalf("publish failed: %#v, %v", published, err)
	}

	limit := 5
	polled, err := client.Poll(ctx, "consumer-a", "instance-a", &limit)
	if err != nil || !polled.LockAcquired {
		t.Fatalf("poll failed: %#v, %v", polled, err)
	}

	committed, err := client.Commit(ctx, "consumer-a", "instance-a", 7)
	if err != nil || !committed.Committed {
		t.Fatalf("commit failed: %#v, %v", committed, err)
	}

	recovered, err := client.Recover(ctx, "consumer-a", time.Date(2026, 5, 16, 0, 0, 0, 0, time.UTC))
	if err != nil || recovered.InitializedOffset != 7 {
		t.Fatalf("recover failed: %#v, %v", recovered, err)
	}

	maintenance, err := client.RunMaintenance(ctx)
	if err != nil || !maintenance.Executed {
		t.Fatalf("maintenance failed: %#v, %v", maintenance, err)
	}

	if _, err := client.EnsureCurrentPartition(ctx); err != nil {
		t.Fatalf("ensure current failed: %v", err)
	}
	if _, err := client.EnsureFuturePartitions(ctx); err != nil {
		t.Fatalf("ensure future failed: %v", err)
	}
	if _, err := client.DropOldPartitions(ctx, 3); err != nil {
		t.Fatalf("drop partitions failed: %v", err)
	}

	cleaned, err := client.CleanupConsumed(ctx)
	if err != nil || cleaned != 17 {
		t.Fatalf("cleanup failed: %d, %v", cleaned, err)
	}
}
