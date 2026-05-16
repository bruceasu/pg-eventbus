package pgeventbus

import (
	"encoding/json"
	"time"
)

type contract.me.asu.pg_eventbus.ApiResponse[T any] struct {
	Success   bool      `json:"success"`
	Code      string    `json:"code"`
	Message   string    `json:"message"`
	RequestID string    `json:"requestId"`
	Timestamp time.Time `json:"timestamp"`
	Data      T         `json:"data"`
	Error     *contract.me.asu.pg_eventbus.ErrorDto `json:"error"`
}

type contract.me.asu.pg_eventbus.ErrorDto struct {
	ErrorCode    string          `json:"errorCode"`
	ErrorMessage string          `json:"errorMessage"`
	Details      []string        `json:"details"`
	FieldErrors  []contract.me.asu.pg_eventbus.FieldErrorDto `json:"fieldErrors"`
}

type contract.me.asu.pg_eventbus.FieldErrorDto struct {
	Field         string `json:"field"`
	RejectedValue string `json:"rejectedValue"`
	Message       string `json:"message"`
}

type contract.me.asu.pg_eventbus.EventDto struct {
	ID           int64           `json:"id"`
	SourceSystem string          `json:"sourceSystem"`
	TargetSystem string          `json:"targetSystem"`
	EventType    string          `json:"eventType"`
	Payload      json.RawMessage `json:"payload"`
	CreatedAt    time.Time       `json:"createdAt"`
}

type contract.me.asu.pg_eventbus.ConsumerDto struct {
	ConsumerName         string     `json:"consumerName"`
	InstanceID           string     `json:"instanceId"`
	Locked               bool       `json:"locked"`
	LockOwner            string     `json:"lockOwner"`
	LockAcquiredAt       *time.Time `json:"lockAcquiredAt"`
	LockExpiresAt        *time.Time `json:"lockExpiresAt"`
	LastCommittedEventID int64      `json:"lastCommittedEventId"`
	CurrentOffset        int64      `json:"currentOffset"`
	BatchSize            int        `json:"batchSize"`
}

type contract.me.asu.pg_eventbus.PublishResultDto struct {
	EventID int64 `json:"eventId"`
}

type contract.me.asu.pg_eventbus.PollResultDto struct {
	Consumer     *contract.me.asu.pg_eventbus.ConsumerDto `json:"consumer"`
	Events       []contract.me.asu.pg_eventbus.EventDto   `json:"events"`
	BatchSize    int          `json:"batchSize"`
	LockAcquired bool         `json:"lockAcquired"`
	PolledAt     time.Time    `json:"polledAt"`
}

type contract.me.asu.pg_eventbus.CommitResultDto struct {
	Committed               bool  `json:"committed"`
	NewLastCommittedEventID int64 `json:"newLastCommittedEventId"`
}

type contract.me.asu.pg_eventbus.RecoverResultDto struct {
	InitializedOffset int64        `json:"initializedOffset"`
	Consumer          *contract.me.asu.pg_eventbus.ConsumerDto `json:"consumer"`
}

type contract.me.asu.pg_eventbus.MaintenanceResultDto struct {
	Executed    bool      `json:"executed"`
	Actions     []string  `json:"actions"`
	CompletedAt time.Time `json:"completedAt"`
}