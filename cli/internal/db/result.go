package db

import (
	"encoding/json"
	"fmt"
	"strconv"
	"time"
)

// Timestamp preserves Jackson JavaTimeModule's numeric Instant representation.
// Match its signed epoch-second + positive nano field encoding, even before 1970.
type Timestamp struct { time.Time }

func (t Timestamp) MarshalJSON() ([]byte, error) {
	seconds, nanos := t.Unix(), int64(t.Nanosecond())
	if seconds == 0 && nanos == 0 { return []byte("0.0"), nil }
	return []byte(strconv.FormatInt(seconds, 10) + fmt.Sprintf(".%09d", nanos)), nil
}

func now() Timestamp { return Timestamp{time.Now()} }

type Consumer struct {
	ConsumerName string `json:"consumerName"`
	InstanceID string `json:"instanceId"`
	Locked bool `json:"locked"`
	LockOwner *string `json:"lockOwner"`
	LockAcquiredAt *Timestamp `json:"lockAcquiredAt"`
	LockExpiresAt *Timestamp `json:"lockExpiresAt"`
	LastCommittedEventID int64 `json:"lastCommittedEventId"`
	CurrentOffset int64 `json:"currentOffset"`
	BatchSize int `json:"batchSize"`
}

type Event struct {
	ID int64 `json:"id"`
	SourceSystem string `json:"sourceSystem"`
	TargetSystem *string `json:"targetSystem"`
	EventType string `json:"eventType"`
	Payload json.RawMessage `json:"payload"`
	CreatedAt Timestamp `json:"createdAt"`
}

type PollResult struct {
	Consumer Consumer `json:"consumer"`
	Events []Event `json:"events"`
	BatchSize int `json:"batchSize"`
	LockAcquired bool `json:"lockAcquired"`
	PolledAt Timestamp `json:"polledAt"`
}

type PublishResult struct { EventID int64 `json:"eventId"` }
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
	CompletedAt Timestamp `json:"completedAt"`
}

func consumer(name, instance string, locked bool, batch int, offset int64) Consumer {
	c := Consumer{ConsumerName: name, InstanceID: instance, Locked: locked, BatchSize: batch,
		LastCommittedEventID: offset, CurrentOffset: offset}
	if locked { c.LockOwner = &instance }
	return c
}
