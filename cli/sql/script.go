package installsql

import _ "embed"

//go:generate go run ../internal/cmd/gensql -source ../../docs/sql -output init.sql

// Template is embedded at compile time; there is no runtime filesystem fallback.
//go:embed init.sql
var Template string
