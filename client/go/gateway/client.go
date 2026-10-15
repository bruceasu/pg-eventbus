package pgeventbus

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
)

type Client struct {
	baseURL    string
	httpClient *http.Client
}

func New(baseURL string) *Client {
	return &Client{
		baseURL:    strings.TrimRight(baseURL, "/"),
		httpClient: &http.Client{Timeout: 30 * time.Second},
	}
}

func NewWithHTTPClient(baseURL string, httpClient *http.Client) *Client {
	if httpClient == nil {
		httpClient = &http.Client{Timeout: 30 * time.Second}
	}
	return &Client{
		baseURL:    strings.TrimRight(baseURL, "/"),
		httpClient: httpClient,
	}
}

func (c *Client) Publish(ctx context.Context, sourceSystem, targetSystem, eventType string, payload json.RawMessage) (PublishResultDto, error) {
	var result PublishResultDto
	err := c.doJSON(ctx, http.MethodPost, "/api/event-bus/events", map[string]any{
		"sourceSystem": sourceSystem,
		"targetSystem": targetSystem,
		"eventType":    eventType,
		"payload":      json.RawMessage(payload),
	}, &result)
	return result, err
}

func (c *Client) Poll(ctx context.Context, consumerName, instanceID string, limit *int) (PollResultDto, error) {
	var result PollResultDto
	body := map[string]any{
		"instanceId": instanceID,
	}
	if limit != nil {
		body["limit"] = *limit
	}
	err := c.doJSON(ctx, http.MethodPost, "/api/event-bus/consumers/"+url.PathEscape(consumerName)+"/poll", body, &result)
	return result, err
}

func (c *Client) Commit(ctx context.Context, consumerName, instanceID string, newLastID int64) (CommitResultDto, error) {
	var result CommitResultDto
	err := c.doJSON(ctx, http.MethodPost, "/api/event-bus/consumers/"+url.PathEscape(consumerName)+"/commit", map[string]any{
		"instanceId": instanceID,
		"newLastId":  newLastID,
	}, &result)
	return result, err
}

func (c *Client) Recover(ctx context.Context, consumerName string, startTime time.Time) (RecoverResultDto, error) {
	var result RecoverResultDto
	err := c.doJSON(ctx, http.MethodPost, "/api/event-bus/consumers/"+url.PathEscape(consumerName)+"/recover", map[string]any{
		"startTime": startTime.UTC().Format(time.RFC3339Nano),
	}, &result)
	return result, err
}

func (c *Client) RunMaintenance(ctx context.Context) (MaintenanceResultDto, error) {
	var result MaintenanceResultDto
	err := c.doJSON(ctx, http.MethodPost, "/api/event-bus/maintenance/run", nil, &result)
	return result, err
}

func (c *Client) EnsureCurrentPartition(ctx context.Context) (MaintenanceResultDto, error) {
	var result MaintenanceResultDto
	err := c.doJSON(ctx, http.MethodPost, "/api/event-bus/maintenance/ensure-current-partition", nil, &result)
	return result, err
}

func (c *Client) EnsureFuturePartitions(ctx context.Context) (MaintenanceResultDto, error) {
	var result MaintenanceResultDto
	err := c.doJSON(ctx, http.MethodPost, "/api/event-bus/maintenance/ensure-future-partitions", nil, &result)
	return result, err
}

func (c *Client) DropOldPartitions(ctx context.Context, keepMonths int) (MaintenanceResultDto, error) {
	var result MaintenanceResultDto
	err := c.doJSON(ctx, http.MethodPost, fmt.Sprintf("/api/event-bus/maintenance/drop-older-than/%d", keepMonths), nil, &result)
	return result, err
}

func (c *Client) CleanupConsumed(ctx context.Context) (int, error) {
	var result int
	err := c.doJSON(ctx, http.MethodPost, "/api/event-bus/maintenance/cleanup-consumed", nil, &result)
	return result, err
}

func (c *Client) doJSON(ctx context.Context, method, path string, body any, out any) error {
	var reader io.Reader
	if body != nil {
		raw, err := json.Marshal(body)
		if err != nil {
			return err
		}
		reader = bytes.NewReader(raw)
	}

	req, err := http.NewRequestWithContext(ctx, method, c.baseURL+path, reader)
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	rawBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return err
	}
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("unexpected http status %d: %s", resp.StatusCode, strings.TrimSpace(string(rawBody)))
	}

	var envelope ApiResponse[json.RawMessage]
	if err := json.Unmarshal(rawBody, &envelope); err != nil {
		return err
	}
	if !envelope.Success {
		apiErr := &APIError{Code: envelope.Code, Message: envelope.Message}
		if envelope.Error != nil {
			apiErr.Code = envelope.Error.ErrorCode
			apiErr.Message = envelope.Error.ErrorMessage
			apiErr.Details = envelope.Error.Details
		}
		return apiErr
	}
	if out == nil || len(envelope.Data) == 0 {
		return nil
	}
	return json.Unmarshal(envelope.Data, out)
}