package pgeventbus

import (
	"encoding/json"
	"time"
)

type ApiResponse[T any] struct {
	Success   bool      `json:"success"`
	Code      string    `json:"code"`
	Message   string    `json:"message"`
	RequestID string    `json:"requestId"`
	Timestamp time.Time `json:"timestamp"`
	Data      T         `json:"data"`
	Error     *ErrorDto `json:"error"`
}

type ErrorDto struct {
	ErrorCode    string          `json:"errorCode"`
	ErrorMessage string          `json:"errorMessage"`
	Details      []string        `json:"details"`
	FieldErrors  []FieldErrorDto `json:"fieldErrors"`
}

type FieldErrorDto struct {
	Field         string `json:"field"`
	RejectedValue string `json:"rejectedValue"`
	Message       string `json:"message"`
}

type EventDto struct {
	ID           int64           `json:"id"`
	SourceSystem string          `json:"sourceSystem"`
	TargetSystem string          `json:"targetSystem"`
	EventType    string          `json:"eventType"`
	Payload      json.RawMessage `json:"payload"`
	CreatedAt    time.Time       `json:"createdAt"`
}

type ConsumerDto struct {
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

type PublishResultDto struct {
	EventID int64 `json:"eventId"`
}

type PollResultDto struct {
	Consumer     *ConsumerDto `json:"consumer"`
	Events       []EventDto   `json:"events"`
	BatchSize    int          `json:"batchSize"`
	LockAcquired bool         `json:"lockAcquired"`
	PolledAt     time.Time    `json:"polledAt"`
}

type CommitResultDto struct {
	Committed               bool  `json:"committed"`
	NewLastCommittedEventID int64 `json:"newLastCommittedEventId"`
}

type RecoverResultDto struct {
	InitializedOffset int64        `json:"initializedOffset"`
	Consumer          *ConsumerDto `json:"consumer"`
}

type MaintenanceResultDto struct {
	Executed    bool      `json:"executed"`
	Actions     []string  `json:"actions"`
	CompletedAt time.Time `json:"completedAt"`
}