package db

import (
	"context"
	"fmt"
)

func (c *Connection) Maintain(ctx context.Context, action string, months int32) (MaintenanceResult, error) {
	var name, label, placeholders string
	var arguments []any
	switch action {
	case "partition ensure-current": name, label = "ensure_current_partition", "ensure_current_partition"
	case "partition ensure-future": name, label, placeholders, arguments = "ensure_future_partitions", "ensure_future_partitions:6", "$1", []any{int32(6)}
	case "partition drop-older-than":
		if months < 0 { return MaintenanceResult{}, fmt.Errorf("keepMonths must be nonnegative") }
		name, label, placeholders, arguments = "drop_partition_older_than", fmt.Sprintf("drop_partition_older_than:%d", months), "$1", []any{months}
	case "maintenance run": name, label = "maintenance", "maintenance"
	default: return MaintenanceResult{}, fmt.Errorf("unsupported maintenance operation")
	}
	if _, err := c.conn.Exec(ctx, "select " + c.function(name) + "(" + placeholders + ")", arguments...); err != nil { return MaintenanceResult{}, safeError(name, err) }
	return MaintenanceResult{Executed: true, Actions: []string{label}, CompletedAt: now()}, nil
}
