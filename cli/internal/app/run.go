package app

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"strconv"
	"time"

	"github.com/bruceasu/pg-eventbus/cli/internal/db"
)

func Run(ctx context.Context, args []string, stdout, stderr io.Writer) int {
	c, err := parse(args)
	if err != nil { fmt.Fprintln(stderr, err); return 2 }
	if c.help { fmt.Fprint(stdout, usage); return 0 }
	cfg, err := loadConfiguration(c)
	if err != nil {
		fmt.Fprintln(stderr, err)
		var file *fileError
		if errors.As(err, &file) { return 1 }
		return 2
	}
	root := ctx
	if cfg.timeout > 0 {
		var cancel context.CancelFunc
		ctx, cancel = context.WithTimeout(ctx, cfg.timeout)
		defer cancel()
	}
	connection, err := db.Open(ctx, cfg.databaseOptions())
	if err != nil { return failure(root, err, stderr) }
	defer connection.Close()
	var result any
	switch c.kind {
	case "db init": err = connection.Initialize(ctx, cfg.owner)
	case "event publish": result, err = connection.Publish(ctx, c.args[0], c.args[1], c.args[2], json.RawMessage(c.args[3]))
	case "event commit":
		offset, _ := strconv.ParseInt(c.args[2], 10, 64)
		result, err = connection.Commit(ctx, c.args[0], c.args[1], offset)
	case "event recover":
		start, _ := time.Parse(time.RFC3339Nano, c.args[1])
		result, err = connection.Recover(ctx, c.args[0], start)
	case "event poll":
		var limit int64
		if len(c.args) == 3 { limit, _ = strconv.ParseInt(c.args[2], 10, 32) }
		result, err = connection.Poll(ctx, c.args[0], c.args[1], int32(limit))
	default:
		var months int64
		if len(c.args) == 1 { months, _ = strconv.ParseInt(c.args[0], 10, 32) }
		result, err = connection.Maintain(ctx, c.kind, int32(months))
	}
	if err != nil { return failure(root, err, stderr) }
	if root.Err() != nil { return failure(root, root.Err(), stderr) }
	if ctx.Err() != nil { return failure(root, ctx.Err(), stderr) }
	if c.kind == "db init" {
		_, err = fmt.Fprintln(stdout, "db init completed")
		if err != nil { fmt.Fprintln(stderr, "cannot write initialization result"); return 1 }
	} else if err = writeResult(stdout, result); err != nil { return failure(root, err, stderr) }
	return 0
}

func failure(root context.Context, err error, stderr io.Writer) int {
	if root.Err() != nil { fmt.Fprintln(stderr, "command interrupted"); return 130 }
	fmt.Fprintln(stderr, err)
	var config *db.ConfigError
	if errors.As(err, &config) { return 2 }
	return 1
}
