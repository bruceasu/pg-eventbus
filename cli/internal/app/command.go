package app

import (
	"encoding/json"
	"fmt"
	"strconv"
	"strings"
	"time"
)

const usage = `Usage: pg-eventbus-cli [options] <command>
  db init [owner]
  partition ensure-current
  partition ensure-future
  partition drop-older-than <keepMonths>
  event send|publish <source> <target> <type> <payloadJson>
  event get|poll <consumerName> <instanceId> [limit]
  event set-cursor|commit <consumerName> <instanceId> <newLastId>
  event recover <consumerName> <isoInstant>
  maintenance run
Options: --config <path>, --timeout <duration>, --spring.datasource.url=<url>,
  --spring.datasource.username=<name>, --spring.datasource.password=<password>,
  --bus.schema=<name>, --bus.batch-size=<n>, --bus.lock-ttl-seconds=<n>
`

type command struct {
	kind string
	args []string
	options map[string]string
	help bool
}

var optionKeys = map[string]bool{
	"config": true, "timeout": true,
	"spring.datasource.url": true, "spring.datasource.username": true,
	"spring.datasource.password": true, "spring.datasource.driver-class-name": true,
	"bus.schema": true, "bus.batch-size": true, "bus.lock-ttl-seconds": true,
}

func parse(args []string) (command, error) {
	c := command{options: map[string]string{}}
	var positionals []string
	literal := false
	for i := 0; i < len(args); i++ {
		arg := args[i]
		if !literal && arg == "--" { literal = true; continue }
		if !literal && (arg == "--help" || arg == "-h") { c.help = true; continue }
		if !literal && strings.HasPrefix(arg, "--") {
			key, value, assigned := strings.Cut(strings.TrimPrefix(arg, "--"), "=")
			if !optionKeys[key] { return c, fmt.Errorf("unsupported command option") }
			if !assigned {
				if i+1 == len(args) || strings.HasPrefix(args[i+1], "--") { return c, fmt.Errorf("missing value for --%s", key) }
				i++; value = args[i]
			}
			c.options[key] = value
		} else { positionals = append(positionals, arg) }
	}
	if c.help || len(positionals) == 0 { c.help = true; return c, nil }
	if len(positionals) < 2 { return c, fmt.Errorf("missing subcommand") }
	c.kind = positionals[0] + " " + positionals[1]
	c.args = positionals[2:]
	min, max := 0, 0
	switch c.kind {
	case "db init": max = 1
	case "partition ensure-current", "partition ensure-future", "maintenance run":
	case "partition drop-older-than": min, max = 1, 1
	case "event send", "event publish": c.kind = "event publish"; min, max = 4, 4
	case "event get", "event poll": c.kind = "event poll"; min, max = 2, 3
	case "event set-cursor", "event commit": c.kind = "event commit"; min, max = 3, 3
	case "event recover": min, max = 2, 2
	default: return c, fmt.Errorf("unknown command")
	}
	if len(c.args) < min || len(c.args) > max { return c, fmt.Errorf("invalid argument count for %s", c.kind) }
	return c, validateCommand(c)
}

func validateCommand(c command) error {
	switch c.kind {
	case "event publish":
		if !json.Valid([]byte(c.args[3])) { return fmt.Errorf("payload must be one valid JSON value") }
	case "event recover":
		if _, err := time.Parse(time.RFC3339Nano, c.args[1]); err != nil { return fmt.Errorf("start time must be RFC3339 with timezone") }
	case "event commit":
		if _, err := strconv.ParseInt(c.args[2], 10, 64); err != nil { return fmt.Errorf("offset must be an int64") }
	case "event poll":
		if len(c.args) == 3 {
			if _, err := strconv.ParseInt(c.args[2], 10, 32); err != nil { return fmt.Errorf("limit must be an int32") }
		}
	case "partition drop-older-than":
		months, err := strconv.ParseInt(c.args[0], 10, 32)
		if err != nil || months < 0 { return fmt.Errorf("keepMonths must be a nonnegative int32") }
	}
	return nil
}
