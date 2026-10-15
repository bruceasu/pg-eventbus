package db

import (
	"context"
	"fmt"
	"strings"
	"time"

	installsql "github.com/bruceasu/pg-eventbus/cli/sql"
	"github.com/jackc/pgx/v5"
)

func (c *Connection) Initialize(ctx context.Context, owner string) error {
	if !InstallIdentifier(c.schema) || !InstallIdentifier(owner) { return fmt.Errorf("invalid installation schema or role") }
	// The role is a SQL string argument to format(%I) inside DO, not an SQL identifier here.
	roleLiteral := "'" + strings.ReplaceAll(owner, "'", "''") + "'"
	script := strings.NewReplacer("{{schema_ident}}", pgx.Identifier{c.schema}.Sanitize(), "{{owner_literal}}", roleLiteral).Replace(installsql.Template)
	_, err := c.conn.Exec(ctx, script, pgx.QueryExecModeSimpleProtocol)
	if err != nil {
		// The canceled command context cannot be used to recover an open transaction.
		cleanup, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		_, _ = c.conn.Exec(cleanup, "ROLLBACK", pgx.QueryExecModeSimpleProtocol)
		return safeError("initialize", err)
	}
	return nil
}

func InstallIdentifier(value string) bool {
	if value == "" { return false }
	for i, ch := range value {
		if ch == '_' || ch >= 'A' && ch <= 'Z' || ch >= 'a' && ch <= 'z' || i > 0 && ch >= '0' && ch <= '9' { continue }
		return false
	}
	return true
}
