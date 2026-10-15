// Package api implements the HTTP contract without exposing driver diagnostics.
package api

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"mime"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/bruceasu/pg-eventbus/gateway/internal/store"
)

type Service interface {
	Publish(context.Context, string, string, string, json.RawMessage) (store.PublishResult, error)
	Poll(context.Context, string, string, int32) (store.PollResult, error)
	Commit(context.Context, string, string, int64) (store.CommitResult, error)
	Recover(context.Context, string, time.Time) (store.RecoverResult, error)
	Maintain(context.Context, string, int32) (store.MaintenanceResult, error)
	CleanupConsumed(context.Context) (int32, error)
}

type envelope struct {
	Success bool `json:"success"`
	Code string `json:"code"`
	Message string `json:"message"`
	RequestID *string `json:"requestId"`
	Timestamp time.Time `json:"timestamp"`
	Data any `json:"data"`
	Error *errorBody `json:"error"`
}
type errorBody struct {
	ErrorCode string `json:"errorCode"`
	ErrorMessage string `json:"errorMessage"`
	Details []string `json:"details"`
	FieldErrors []any `json:"fieldErrors"`
}

func write(w http.ResponseWriter, status int, response envelope) {
	response.Timestamp = time.Now().UTC()
	body, err := json.Marshal(response)
	if err != nil {
		// Marshal before writing headers so invalid database JSON cannot send a partial success.
		fail(w, http.StatusInternalServerError, "INTERNAL_ERROR", "could not encode response")
		return
	}
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("X-Content-Type-Options", "nosniff")
	w.WriteHeader(status)
	_, _ = w.Write(append(body, '\n'))
}

func fail(w http.ResponseWriter, status int, code, message string) {
	write(w, status, envelope{Code: code, Message: message,
		Error: &errorBody{ErrorCode: code, ErrorMessage: message, Details: []string{}, FieldErrors: []any{}}})
}

func complete(w http.ResponseWriter, r *http.Request, data any, err error, message string) {
	if err != nil {
		if errors.Is(err, context.DeadlineExceeded) || errors.Is(r.Context().Err(), context.DeadlineExceeded) {
			fail(w, http.StatusGatewayTimeout, "TIMEOUT", "database operation timed out")
		} else {
			fail(w, http.StatusInternalServerError, "DATABASE_ERROR", "database operation failed")
		}
		return
	}
	write(w, http.StatusOK, envelope{Success: true, Code: "OK", Message: message, Data: data})
}

func decode(w http.ResponseWriter, r *http.Request, target any) bool {
	media, _, err := mime.ParseMediaType(r.Header.Get("Content-Type"))
	if err != nil || (media != "application/json" && !(strings.HasPrefix(media, "application/") && strings.HasSuffix(media, "+json"))) {
		fail(w, http.StatusUnsupportedMediaType, "UNSUPPORTED_MEDIA_TYPE", "application/json is required")
		return false
	}
	r.Body = http.MaxBytesReader(w, r.Body, 1<<20)
	defer r.Body.Close()
	body, err := io.ReadAll(r.Body)
	if err != nil {
		var tooLarge *http.MaxBytesError
		if errors.As(err, &tooLarge) {
			fail(w, http.StatusRequestEntityTooLarge, "BODY_TOO_LARGE", "JSON body exceeds 1 MiB")
		} else { fail(w, http.StatusBadRequest, "BAD_REQUEST", "could not read JSON body") }
		return false
	}
	if strings.TrimSpace(string(body)) == "null" || json.Unmarshal(body, target) != nil {
		fail(w, http.StatusBadRequest, "BAD_REQUEST", "a valid JSON object with correctly typed fields is required")
		return false
	}
	return true
}

func required(w http.ResponseWriter, fields ...string) bool {
	for _, value := range fields {
		if strings.TrimSpace(value) == "" {
			fail(w, http.StatusBadRequest, "BAD_REQUEST", "required string fields must not be blank")
			return false
		}
	}
	return true
}

func New(service Service, timeout time.Duration) http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("POST /api/event-bus/events", func(w http.ResponseWriter, r *http.Request) {
		var input struct {
			SourceSystem string `json:"sourceSystem"`
			TargetSystem string `json:"targetSystem"`
			EventType string `json:"eventType"`
			Payload json.RawMessage `json:"payload"`
		}
		if !decode(w, r, &input) || !required(w, input.SourceSystem, input.TargetSystem, input.EventType) { return }
		if len(input.Payload) == 0 || strings.TrimSpace(string(input.Payload)) == "null" {
			fail(w, http.StatusBadRequest, "BAD_REQUEST", "payload must not be null")
			return
		}
		result, err := service.Publish(r.Context(), input.SourceSystem, input.TargetSystem, input.EventType, input.Payload)
		complete(w, r, result, err, "event published")
	})
	mux.HandleFunc("POST /api/event-bus/consumers/{consumerName}/poll", func(w http.ResponseWriter, r *http.Request) {
		var input struct {
			InstanceID string `json:"instanceId"`
			Limit *int32 `json:"limit"`
		}
		if !decode(w, r, &input) || !required(w, input.InstanceID) { return }
		var limit int32
		if input.Limit != nil { limit = *input.Limit }
		result, err := service.Poll(r.Context(), r.PathValue("consumerName"), input.InstanceID, limit)
		complete(w, r, result, err, "poll completed")
	})
	mux.HandleFunc("POST /api/event-bus/consumers/{consumerName}/commit", func(w http.ResponseWriter, r *http.Request) {
		var input struct {
			InstanceID string `json:"instanceId"`
			NewLastID *int64 `json:"newLastId"`
		}
		if !decode(w, r, &input) || !required(w, input.InstanceID) { return }
		if input.NewLastID == nil { fail(w, http.StatusBadRequest, "BAD_REQUEST", "newLastId is required"); return }
		result, err := service.Commit(r.Context(), r.PathValue("consumerName"), input.InstanceID, *input.NewLastID)
		complete(w, r, result, err, "commit completed")
	})
	mux.HandleFunc("POST /api/event-bus/consumers/{consumerName}/recover", func(w http.ResponseWriter, r *http.Request) {
		var input struct { StartTime json.RawMessage `json:"startTime"` }
		if !decode(w, r, &input) { return }
		start, err := parseInstant(input.StartTime)
		if err != nil {
			fail(w, http.StatusBadRequest, "BAD_REQUEST", "startTime must be an RFC3339 timestamp or numeric epoch seconds")
			return
		}
		result, err := service.Recover(r.Context(), r.PathValue("consumerName"), start)
		complete(w, r, result, err, "consumer recovered")
	})
	for _, route := range []struct { path, operation, message string }{
		{"run", "run", "maintenance completed"},
		{"ensure-current-partition", "current", "current partition ensured"},
		{"ensure-future-partitions", "future", "future partitions ensured"},
		{"drop-older-than/{keepMonths}", "drop", "old partitions dropped"},
	} {
		mux.HandleFunc("POST /api/event-bus/maintenance/" + route.path, func(w http.ResponseWriter, r *http.Request) {
			var months int64
			if route.operation == "drop" {
				var err error
				months, err = strconv.ParseInt(r.PathValue("keepMonths"), 10, 32)
				if err != nil { fail(w, http.StatusBadRequest, "BAD_REQUEST", "keepMonths must be an int32"); return }
			}
			result, err := service.Maintain(r.Context(), route.operation, int32(months))
			complete(w, r, result, err, route.message)
		})
	}
	mux.HandleFunc("POST /api/event-bus/maintenance/cleanup-consumed", func(w http.ResponseWriter, r *http.Request) {
		result, err := service.CleanupConsumed(r.Context())
		complete(w, r, result, err, "consumed rows cleaned")
	})
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		defer func() {
			if recover() != nil { fail(w, http.StatusInternalServerError, "INTERNAL_ERROR", "request failed") }
		}()
		ctx, cancel := context.WithTimeout(r.Context(), timeout)
		defer cancel()
		mux.ServeHTTP(w, r.WithContext(ctx))
	})
}
