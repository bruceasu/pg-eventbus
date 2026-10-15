package db

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
)

type Options struct {
	URL string
	Username, Password *string
	Schema string
	BatchSize, TTL int
}

type ConfigError struct{}
func (*ConfigError) Error() string { return "invalid PostgreSQL connection configuration" }

type Connection struct {
	conn *pgx.Conn
	schema string
	batchSize, ttl int
}

func Open(ctx context.Context, options Options) (*Connection, error) {
	config, err := pgx.ParseConfig(options.URL)
	if err != nil { return nil, &ConfigError{} }
	if options.Username != nil { config.User = *options.Username }
	if options.Password != nil { config.Password = *options.Password }
	// Startup is bounded, while command execution is controlled by --timeout.
	if config.ConnectTimeout == 0 { config.ConnectTimeout = 10*time.Second }
	conn, err := pgx.ConnectConfig(ctx, config)
	if err != nil { return nil, safeError("connect", err) }
	return &Connection{conn: conn, schema: options.Schema, batchSize: options.BatchSize, ttl: options.TTL}, nil
}

func (c *Connection) Close() {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	_ = c.conn.Close(ctx)
}

func (c *Connection) function(name string) string {
	return pgx.Identifier{c.schema, name}.Sanitize()
}

// Do not include DSNs, supplied data, server messages or driver errors in diagnostics.
func safeError(operation string, err error) error {
	if errors.Is(err, context.Canceled) { return fmt.Errorf("%s: %w", operation, context.Canceled) }
	if errors.Is(err, context.DeadlineExceeded) { return fmt.Errorf("%s: %w", operation, context.DeadlineExceeded) }
	var server *pgconn.PgError
	if errors.As(err, &server) && len(server.Code) == 5 && strings.IndexFunc(server.Code, func(r rune) bool { return !(r >= '0' && r <= '9' || r >= 'A' && r <= 'Z') }) < 0 {
		return fmt.Errorf("%s failed (SQLSTATE %s)", operation, server.Code)
	}
	return fmt.Errorf("%s failed; check database connectivity, permissions and configuration", operation)
}
