package pgeventbus

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"time"
)

type runner interface {
	Run(ctx context.Context, sql string) (string, error)
}

type Client struct {
	runner runner
	schema string
}

func New() *Client {
	return &Client{runner: newDefaultRunner(), schema: resolveSchema("")}
}

func NewWithRunner(r runner) *Client {
	return &Client{runner: r, schema: resolveSchema("")}
}

func NewWithSchema(schema string) *Client {
	return &Client{runner: newDefaultRunner(), schema: resolveSchema(schema)}
}

func NewWithRunnerAndSchema(r runner, schema string) *Client {
	return &Client{runner: r, schema: resolveSchema(schema)}
}

func (c *Client) Publish(ctx context.Context, sourceSystem, targetSystem, eventType string, payload json.RawMessage) (contract.me.asu.pg_eventbus.PublishResultDto, error) {
	var result contract.me.asu.pg_eventbus.PublishResultDto
	err := c.runJSON(ctx, publishSQL(c.schema, sourceSystem, targetSystem, eventType, payload), &result)
	return result, err
}

func (c *Client) Poll(ctx context.Context, consumerName, instanceID string, limit *int) (contract.me.asu.pg_eventbus.PollResultDto, error) {
	var result contract.me.asu.pg_eventbus.PollResultDto
	if limit == nil {
		defaultLimit := 100
		limit = &defaultLimit
	}
	err := c.runJSON(ctx, pollSQL(c.schema, consumerName, instanceID, *limit), &result)
	return result, err
}

func (c *Client) Commit(ctx context.Context, consumerName, instanceID string, newLastID int64) (contract.me.asu.pg_eventbus.CommitResultDto, error) {
	var result contract.me.asu.pg_eventbus.CommitResultDto
	err := c.runJSON(ctx, commitSQL(c.schema, consumerName, instanceID, newLastID), &result)
	return result, err
}

func (c *Client) Recover(ctx context.Context, consumerName string, startTime time.Time) (contract.me.asu.pg_eventbus.RecoverResultDto, error) {
	var result contract.me.asu.pg_eventbus.RecoverResultDto
	err := c.runJSON(ctx, recoverSQL(c.schema, consumerName, startTime), &result)
	return result, err
}

func (c *Client) RunMaintenance(ctx context.Context) (contract.me.asu.pg_eventbus.MaintenanceResultDto, error) {
	var result contract.me.asu.pg_eventbus.MaintenanceResultDto
	err := c.runJSON(ctx, maintenanceSQL(c.schema, "maintenance"), &result)
	return result, err
}

func (c *Client) EnsureCurrentPartition(ctx context.Context) (contract.me.asu.pg_eventbus.MaintenanceResultDto, error) {
	var result contract.me.asu.pg_eventbus.MaintenanceResultDto
	err := c.runJSON(ctx, maintenanceSQL(c.schema, "ensure-current", "SELECT "+c.schemaFunction("ensure_current_partition")+"();"), &result)
	return result, err
}

func (c *Client) EnsureFuturePartitions(ctx context.Context) (contract.me.asu.pg_eventbus.MaintenanceResultDto, error) {
	var result contract.me.asu.pg_eventbus.MaintenanceResultDto
	err := c.runJSON(ctx, maintenanceSQL(c.schema, "ensure-future", "SELECT "+c.schemaFunction("ensure_future_partitions")+"(6);"), &result)
	return result, err
}

func (c *Client) DropOldPartitions(ctx context.Context, keepMonths int) (contract.me.asu.pg_eventbus.MaintenanceResultDto, error) {
	var result contract.me.asu.pg_eventbus.MaintenanceResultDto
	err := c.runJSON(ctx, maintenanceSQL(c.schema, "drop-old", fmt.Sprintf("SELECT %s(%d);", c.schemaFunction("drop_partition_older_than"), keepMonths)), &result)
	return result, err
}

func (c *Client) CleanupConsumed(ctx context.Context) (int, error) {
	return c.runInt(ctx, "SELECT "+c.schemaFunction("cleanup_consumed")+"();")
}

func resolveSchema(schema string) string {
	if value := strings.TrimSpace(schema); value != "" {
		return value
	}
	if value := strings.TrimSpace(os.Getenv("PGEVENTBUS_SCHEMA")); value != "" {
		return value
	}
	return "bus"
}

func (c *Client) runJSON(ctx context.Context, sql string, out any) error {
	raw, err := c.runner.Run(ctx, sql)
	if err != nil {
		return err
	}
	if err := json.Unmarshal([]byte(strings.TrimSpace(raw)), out); err != nil {
		return err
	}
	return nil
}

func (c *Client) runInt(ctx context.Context, sql string) (int, error) {
	raw, err := c.runner.Run(ctx, sql)
	if err != nil {
		return 0, err
	}
	var value int
	if _, err := fmt.Sscanf(strings.TrimSpace(raw), "%d", &value); err != nil {
		return 0, err
	}
	return value, nil
}

type psqlRunner struct {
	command string
}

func newDefaultRunner() runner {
	if cmd := strings.TrimSpace(os.Getenv("PGEVENTBUS_PSQL_COMMAND")); cmd != "" {
		return &psqlRunner{command: cmd}
	}
	if path, err := exec.LookPath("psql"); err == nil {
		return &psqlRunner{command: path}
	}
	if path := localPSQLTool(); path != "" {
		return &psqlRunner{command: path}
	}
	return &psqlRunner{command: "psql"}
}

func localPSQLTool() string {
	baseDir := strings.TrimSpace(os.Getenv("PGEVENTBUS_PGSQL_CLIENT_DIR"))
	if baseDir == "" {
		baseDir = findLocalToolDir()
	}
	if runtime.GOOS == "windows" {
		candidate := filepath.Join(baseDir, "bin", "psql-go.exe")
		if fileExists(candidate) {
			return candidate
		}
	}
	candidate := filepath.Join(baseDir, "bin", "psql-go")
	if fileExists(candidate) {
		return candidate
	}
	return ""
}

func findLocalToolDir() string {
	dir, err := os.Getwd()
	if err != nil {
		return ""
	}
	for {
		candidate := filepath.Join(dir, "tools", "pgsql-client")
		if info, err := os.Stat(candidate); err == nil && info.IsDir() {
			return candidate
		}
		parent := filepath.Dir(dir)
		if parent == dir {
			return ""
		}
		dir = parent
	}
}

func fileExists(path string) bool {
	info, err := os.Stat(path)
	return err == nil && !info.IsDir()
}

func (r *psqlRunner) Run(ctx context.Context, sql string) (string, error) {
	args := []string{"-q", "-t", "--no-psqlrc", "-c", sql}
	cmd := exec.CommandContext(ctx, r.command, args...)
	out, err := cmd.CombinedOutput()
	if err != nil {
		return "", fmt.Errorf("%s: %w: %s", r.command, err, strings.TrimSpace(string(out)))
	}
	return string(out), nil
}

func sqlLiteral(value string) string {
	return "'" + strings.ReplaceAll(value, "'", "''") + "'"
}

func quoteIdentifier(value string) string {
	return `"` + strings.ReplaceAll(strings.TrimSpace(value), `"`, `""`) + `"`
}

func (c *Client) schemaFunction(name string) string {
	return quoteIdentifier(c.schema) + "." + quoteIdentifier(name)
}

func sqlJSON(value json.RawMessage) string {
	if len(value) == 0 {
		return "'{}'::jsonb"
	}
	return sqlLiteral(string(value)) + "::jsonb"
}

func publishSQL(schema, sourceSystem, targetSystem, eventType string, payload json.RawMessage) string {
	return fmt.Sprintf("SELECT json_build_object('eventId', %s(%s, %s, %s, %s));",
		quoteIdentifier(schema)+"."+quoteIdentifier("publish_event"), sqlLiteral(sourceSystem), sqlLiteral(targetSystem), sqlLiteral(eventType), sqlJSON(payload))
}

func pollSQL(schema, consumerName, instanceID string, limit int) string {
	return fmt.Sprintf(`
WITH events AS (
    SELECT id, source_system, target_system, event_type, payload, created_at
      FROM %s(%s, %d)
),
consumer AS (
    SELECT consumer_name, last_event_id, lock_owner, lock_until
      FROM %s
     WHERE consumer_name = %s
)
SELECT json_build_object(
    'consumer', (
        SELECT json_build_object(
            'consumerName', c.consumer_name,
            'instanceId', %s,
            'locked', (c.lock_owner IS NOT NULL AND c.lock_owner = %s AND c.lock_until >= now()),
            'lockOwner', COALESCE(c.lock_owner, ''),
            'lockAcquiredAt', NULL,
            'lockExpiresAt', c.lock_until,
            'lastCommittedEventId', c.last_event_id,
            'currentOffset', c.last_event_id,
            'batchSize', %d
        )
          FROM consumer c
    ),
    'events', COALESCE((
        SELECT json_agg(json_build_object(
            'id', e.id,
            'sourceSystem', e.source_system,
            'targetSystem', e.target_system,
            'eventType', e.event_type,
            'payload', e.payload,
            'createdAt', e.created_at
        ))
          FROM events e
    ), '[]'::json),
    'batchSize', %d,
    'lockAcquired', true,
    'polledAt', now()
);`, quoteIdentifier(schema)+"."+quoteIdentifier("poll_events"), sqlLiteral(consumerName), limit, quoteIdentifier(schema)+"."+quoteIdentifier("consumer_registry"), sqlLiteral(consumerName), sqlLiteral(instanceID), sqlLiteral(instanceID), limit, limit)
}

func commitSQL(schema, consumerName, instanceID string, newLastID int64) string {
	return fmt.Sprintf("SELECT json_build_object('committed', %s(%s, %s, %d), 'newLastCommittedEventId', %d);",
		quoteIdentifier(schema)+"."+quoteIdentifier("commit_consumer_offset"), sqlLiteral(consumerName), sqlLiteral(instanceID), newLastID, newLastID)
}

func recoverSQL(schema, consumerName string, startTime time.Time) string {
	ts := sqlLiteral(startTime.UTC().Format(time.RFC3339Nano))
	return fmt.Sprintf(`
WITH init AS (
    SELECT %s(%s, %s::timestamptz) AS initialized_offset
),
consumer AS (
    SELECT consumer_name, last_event_id, lock_owner, lock_until
      FROM %s
     WHERE consumer_name = %s
)
SELECT json_build_object(
    'initializedOffset', (SELECT initialized_offset FROM init),
    'consumer', (
        SELECT json_build_object(
            'consumerName', c.consumer_name,
            'instanceId', '',
            'locked', false,
            'lockOwner', COALESCE(c.lock_owner, ''),
            'lockAcquiredAt', NULL,
            'lockExpiresAt', c.lock_until,
            'lastCommittedEventId', c.last_event_id,
            'currentOffset', c.last_event_id,
            'batchSize', 0
        )
          FROM consumer c
    )
);`, quoteIdentifier(schema)+"."+quoteIdentifier("init_consumer_at_time"), sqlLiteral(consumerName), ts, quoteIdentifier(schema)+"."+quoteIdentifier("consumer_registry"), sqlLiteral(consumerName))
}

func maintenanceSQL(schema, action string, sideEffects ...string) string {
	statement := ""
	if len(sideEffects) > 0 {
		statement = sideEffects[0]
	}
	if statement == "" {
		statement = "SELECT " + quoteIdentifier(schema) + "." + quoteIdentifier("maintenance") + "();"
	}
	return fmt.Sprintf(`
WITH effect AS (
    %s
)
SELECT json_build_object(
    'executed', true,
    'actions', json_build_array(%s),
    'completedAt', now()
);`, statement, sqlLiteral(action))
}
