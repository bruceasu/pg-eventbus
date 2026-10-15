package app

import (
	"encoding/json"
	"fmt"
	"io"
)

func writeResult(stdout io.Writer, value any) error {
	// Encode completely before touching stdout, so an encoding error emits no partial JSON.
	data, err := json.Marshal(value)
	if err != nil { return fmt.Errorf("cannot encode command result") }
	data = append(data, '\n')
	if _, err = stdout.Write(data); err != nil { return fmt.Errorf("cannot write command result") }
	return nil
}
