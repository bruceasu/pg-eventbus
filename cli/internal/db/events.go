package db

import (
	"context"
	"encoding/json"
	"time"
)

func (c *Connection) Publish(ctx context.Context, source, target, kind string, payload json.RawMessage) (PublishResult, error) {
	var result PublishResult
	err := c.conn.QueryRow(ctx, "select " + c.function("publish_event") + "($1, $2, $3, $4::jsonb)", source, target, kind, payload).Scan(&result.EventID)
	if err != nil { return result, safeError("publish", err) }
	return result, nil
}

func (c *Connection) Commit(ctx context.Context, name, instance string, offset int64) (CommitResult, error) {
	result := CommitResult{NewLastCommittedEventID: offset}
	err := c.conn.QueryRow(ctx, "select " + c.function("commit_consumer_offset") + "($1, $2, $3)", name, instance, offset).Scan(&result.Committed)
	if err != nil { return result, safeError("commit", err) }
	return result, nil
}

func (c *Connection) Recover(ctx context.Context, name string, start time.Time) (RecoverResult, error) {
	var offset int64
	err := c.conn.QueryRow(ctx, "select " + c.function("init_consumer_at_time") + "($1, $2)", name, start).Scan(&offset)
	if err != nil { return RecoverResult{}, safeError("recover", err) }
	return RecoverResult{InitializedOffset: offset, Consumer: consumer(name, "", false, c.batchSize, offset)}, nil
}
