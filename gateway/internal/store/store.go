// Package store owns SQL calls and a concurrent pgx connection pool.
package store

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/bruceasu/pg-eventbus/gateway/internal/config"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type Store struct {
	pool *pgxpool.Pool
	schema string
	batchSize, ttl int32
}

func Open(ctx context.Context, c config.Config) (*Store, error) {
	options, err := pgxpool.ParseConfig(c.DatabaseURL)
	if err != nil { return nil, errors.New("invalid PostgreSQL connection configuration") }
	if c.User != nil { options.ConnConfig.User = *c.User }
	if c.Password != nil { options.ConnConfig.Password = *c.Password }
	if options.ConnConfig.ConnectTimeout == 0 { options.ConnConfig.ConnectTimeout = 10*time.Second }
	pool, err := pgxpool.NewWithConfig(ctx, options)
	if err != nil { return nil, errors.New("could not create database connection pool") }
	if err := pool.Ping(ctx); err != nil {
		pool.Close()
		return nil, errors.New("database connection failed; check connectivity, permissions and configuration")
	}
	return &Store{pool: pool, schema: c.Schema, batchSize: c.BatchSize, ttl: c.LockTTL}, nil
}

func (s *Store) Close() { s.pool.Close() }
func (s *Store) function(name string) string { return pgx.Identifier{s.schema, name}.Sanitize() }

func (s *Store) Publish(ctx context.Context, source, target, kind string, payload json.RawMessage) (PublishResult, error) {
	var result PublishResult
	err := s.pool.QueryRow(ctx, "select " + s.function("publish_event") + "($1, $2, $3, $4::jsonb)",
		source, target, kind, payload).Scan(&result.EventID)
	return result, err
}

func (s *Store) Poll(ctx context.Context, name, instance string, requested int32) (PollResult, error) {
	limit := s.batchSize
	if requested > 0 { limit = requested }
	var locked bool
	err := s.pool.QueryRow(ctx, "select " + s.function("acquire_consumer_lock") + "($1, $2, $3)",
		name, instance, s.ttl).Scan(&locked)
	if err != nil { return PollResult{}, err }
	result := PollResult{Consumer: consumer(name, instance, locked, limit, 0),
		Events: []Event{}, BatchSize: limit, LockAcquired: locked}
	if !locked { result.PolledAt = time.Now().UTC(); return result, nil }
	rows, err := s.pool.Query(ctx, "select * from " + s.function("poll_events") + "($1, $2)", name, limit)
	if err != nil { return PollResult{}, err }
	defer rows.Close()
	for rows.Next() {
		var event Event
		if err := rows.Scan(&event.ID, &event.SourceSystem, &event.TargetSystem, &event.EventType,
			&event.Payload, &event.CreatedAt); err != nil { return PollResult{}, err }
		event.CreatedAt = event.CreatedAt.UTC()
		result.Events = append(result.Events, event)
	}
	if err := rows.Err(); err != nil { return PollResult{}, err }
	result.PolledAt = time.Now().UTC()
	return result, nil
}

func (s *Store) Commit(ctx context.Context, name, instance string, offset int64) (CommitResult, error) {
	result := CommitResult{NewLastCommittedEventID: offset}
	err := s.pool.QueryRow(ctx, "select " + s.function("commit_consumer_offset") + "($1, $2, $3)",
		name, instance, offset).Scan(&result.Committed)
	return result, err
}

func (s *Store) Recover(ctx context.Context, name string, start time.Time) (RecoverResult, error) {
	var offset int64
	err := s.pool.QueryRow(ctx, "select " + s.function("init_consumer_at_time") + "($1, $2)", name, start).Scan(&offset)
	return RecoverResult{InitializedOffset: offset, Consumer: consumer(name, "", false, s.batchSize, offset)}, err
}

// Operation names and labels come exclusively from this allowlist, never a request.
func (s *Store) Maintain(ctx context.Context, operation string, months int32) (MaintenanceResult, error) {
	var name, label, placeholder string
	var arguments []any
	switch operation {
	case "run": name, label = "maintenance", "maintenance"
	case "current": name, label = "ensure_current_partition", "ensure_current_partition"
	case "future": name, label, placeholder, arguments = "ensure_future_partitions", "ensure_future_partitions:6", "$1", []any{int32(6)}
	case "drop": name, label, placeholder, arguments = "drop_partition_older_than", fmt.Sprintf("drop_partition_older_than:%d", months), "$1", []any{months}
	default: return MaintenanceResult{}, errors.New("unsupported maintenance operation")
	}
	_, err := s.pool.Exec(ctx, "select " + s.function(name) + "(" + placeholder + ")", arguments...)
	if err != nil { return MaintenanceResult{}, err }
	return MaintenanceResult{Executed: true, Actions: []string{label}, CompletedAt: time.Now().UTC()}, nil
}

func (s *Store) CleanupConsumed(ctx context.Context) (int32, error) {
	var deleted int32
	err := s.pool.QueryRow(ctx, "select " + s.function("cleanup_consumed") + "($1)", int32(10000)).Scan(&deleted)
	return deleted, err
}
