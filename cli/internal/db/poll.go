package db

import (
	"context"
	"encoding/json"
	"time"
)

func (c *Connection) Poll(ctx context.Context, name, instance string, requested int32) (PollResult, error) {
	limit := c.batchSize
	if requested > 0 { limit = int(requested) }
	var locked bool
	err := c.conn.QueryRow(ctx, "select " + c.function("acquire_consumer_lock") + "($1, $2, $3)", name, instance, int32(c.ttl)).Scan(&locked)
	if err != nil { return PollResult{}, safeError("acquire lease", err) }
	result := PollResult{Consumer: consumer(name, instance, locked, limit, 0), Events: []Event{}, BatchSize: limit, LockAcquired: locked}
	if !locked { result.PolledAt = now(); return result, nil }
	rows, err := c.conn.Query(ctx, "select * from " + c.function("poll_events") + "($1, $2)", name, int32(limit))
	if err != nil { return PollResult{}, safeError("poll", err) }
	defer rows.Close()
	for rows.Next() {
		var event Event
		var raw []byte
		var created time.Time
		if err := rows.Scan(&event.ID, &event.SourceSystem, &event.TargetSystem, &event.EventType, &raw, &created); err != nil { return PollResult{}, safeError("decode event", err) }
		event.Payload = append(json.RawMessage(nil), raw...)
		event.CreatedAt = Timestamp{created}
		result.Events = append(result.Events, event)
	}
	if err := rows.Err(); err != nil { return PollResult{}, safeError("poll", err) }
	result.PolledAt = now()
	return result, nil
}
