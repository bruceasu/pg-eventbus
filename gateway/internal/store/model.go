package store

import (
	"encoding/json"
	"time"
)

type Consumer struct {
	ConsumerName string `json:"consumerName"`
	InstanceID string `json:"instanceId"`
	Locked bool `json:"locked"`
	LockOwner *string `json:"lockOwner"`
	LockAcquiredAt *time.Time `json:"lockAcquiredAt"`
	LockExpiresAt *time.Time `json:"lockExpiresAt"`
	LastCommittedEventID int64 `json:"lastCommittedEventId"`
	CurrentOffset int64 `json:"currentOffset"`
	BatchSize int32 `json:"batchSize"`
}

type Event struct {
	ID int64 `json:"id"`
	SourceSystem string `json:"sourceSystem"`
	TargetSystem *string `json:"targetSystem"`
	EventType string `json:"eventType"`
	Payload json.RawMessage `json:"payload"`
	CreatedAt time.Time `json:"createdAt"`
}

type PublishResult struct { EventID int64 `json:"eventId"` }
type PollResult struct {
	Consumer Consumer `json:"consumer"`
	Events []Event `json:"events"`
	BatchSize int32 `json:"batchSize"`
	LockAcquired bool `json:"lockAcquired"`
	PolledAt time.Time `json:"polledAt"`
}
type CommitResult struct {
	Committed bool `json:"committed"`
	NewLastCommittedEventID int64 `json:"newLastCommittedEventId"`
}
type RecoverResult struct {
	InitializedOffset int64 `json:"initializedOffset"`
	Consumer Consumer `json:"consumer"`
}
type MaintenanceResult struct {
	Executed bool `json:"executed"`
	Actions []string `json:"actions"`
	CompletedAt time.Time `json:"completedAt"`
}

func consumer(name, instance string, locked bool, limit int32, offset int64) Consumer {
	c := Consumer{ConsumerName: name, InstanceID: instance, Locked: locked,
		BatchSize: limit, LastCommittedEventID: offset, CurrentOffset: offset}
	if locked { c.LockOwner = &instance }
	return c
}
