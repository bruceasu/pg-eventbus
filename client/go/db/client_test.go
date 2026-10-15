package pgeventbus

import (
	"context"
	"encoding/json"
	"strings"
	"testing"
	"time"
)

type fakeRunner struct {
	lastSQL string
}

func (r *fakeRunner) Run(_ context.Context, sql string) (string, error) {
	r.lastSQL = sql
	switch {
	case strings.Contains(sql, "publish_event"):
		return `{"eventId":11}`, nil
	case strings.Contains(sql, "poll_events"):
		return `{"consumer":{"consumerName":"consumer-a","instanceId":"instance-a","locked":true,"lockOwner":"instance-a","lockAcquiredAt":null,"lockExpiresAt":null,"lastCommittedEventId":7,"currentOffset":7,"batchSize":5},"events":[],"batchSize":5,"lockAcquired":true,"polledAt":"2026-05-16T00:00:00Z"}`, nil
	case strings.Contains(sql, "commit_consumer_offset"):
		return `{"committed":true,"newLastCommittedEventId":7}`, nil
	case strings.Contains(sql, "init_consumer_at_time"):
		return `{"initializedOffset":7,"consumer":{"consumerName":"consumer-a","instanceId":"","locked":false,"lockOwner":"","lockAcquiredAt":null,"lockExpiresAt":null,"lastCommittedEventId":7,"currentOffset":7,"batchSize":0}}`, nil
	case strings.Contains(sql, "cleanup_consumed"):
		return "17", nil
	default:
		return `{"executed":true,"actions":["maintenance"],"completedAt":"2026-05-16T00:00:00Z"}`, nil
	}
}

func TestFlatAPI(t *testing.T) {
	runner := &fakeRunner{}
	client := NewWithRunner(runner)
	ctx := context.Background()

	published, err := client.Publish(ctx, "source", "target", "event", json.RawMessage(`{"hello":"world"}`))
	if err != nil || published.EventID != 11 {
		t.Fatalf("publish failed: %#v %v", published, err)
	}
	if !strings.Contains(runner.lastSQL, `"bus"."publish_event"`) {
		t.Fatalf("expected default bus schema in SQL: %s", runner.lastSQL)
	}

	polled, err := client.Poll(ctx, "consumer-a", "instance-a", func() *int { v := 5; return &v }())
	if err != nil || !polled.LockAcquired {
		t.Fatalf("poll failed: %#v %v", polled, err)
	}

	committed, err := client.Commit(ctx, "consumer-a", "instance-a", 7)
	if err != nil || !committed.Committed {
		t.Fatalf("commit failed: %#v %v", committed, err)
	}

	recovered, err := client.Recover(ctx, "consumer-a", time.Date(2026, 5, 16, 0, 0, 0, 0, time.UTC))
	if err != nil || recovered.InitializedOffset != 7 {
		t.Fatalf("recover failed: %#v %v", recovered, err)
	}

	if _, err := client.RunMaintenance(ctx); err != nil {
		t.Fatalf("maintenance failed: %v", err)
	}
	if _, err := client.EnsureCurrentPartition(ctx); err != nil {
		t.Fatalf("ensure current failed: %v", err)
	}
	if _, err := client.EnsureFuturePartitions(ctx); err != nil {
		t.Fatalf("ensure future failed: %v", err)
	}
	if _, err := client.DropOldPartitions(ctx, 3); err != nil {
		t.Fatalf("drop old failed: %v", err)
	}

	cleaned, err := client.CleanupConsumed(ctx)
	if err != nil || cleaned != 17 {
		t.Fatalf("cleanup failed: %d %v", cleaned, err)
	}
}

func TestCustomSchema(t *testing.T) {
	runner := &fakeRunner{}
	client := NewWithRunnerAndSchema(runner, "analytics")
	ctx := context.Background()

	if _, err := client.RunMaintenance(ctx); err != nil {
		t.Fatalf("maintenance failed: %v", err)
	}
	if !strings.Contains(runner.lastSQL, `"analytics"."maintenance"`) {
		t.Fatalf("expected custom schema in SQL: %s", runner.lastSQL)
	}
}
