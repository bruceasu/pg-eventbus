package pgeventbus

import "strings"

type APIError struct {
	Code    string
	Message string
	Details []string
}

func (e *APIError) Error() string {
	if len(e.Details) == 0 {
		return e.Code + ": " + e.Message
	}
	return e.Code + ": " + e.Message + " (" + strings.Join(e.Details, "; ") + ")"
}
