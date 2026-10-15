package installsql

import _ "embed"

// Template is embedded at compile time; there is no runtime filesystem fallback.
//go:embed init.sql
var Template string
