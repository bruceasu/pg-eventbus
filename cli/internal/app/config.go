package app

import (
    "bytes"
    "fmt"
    "io"
    "net/url"
    "os"
    "path/filepath"
    "strconv"
    "strings"
    "time"

    "github.com/BurntSushi/toml"
    "github.com/bruceasu/pg-eventbus/cli/internal/db"
)

type setting struct { value string; rank int }

type configuration struct {
    url string
    username, password setting
    urlRank int
    schema string
    batchSize, ttl int
    owner string
    timeout time.Duration
}

var environmentKeys = map[string]string{
    "database-url": "PGEVENTBUS_DATABASE_URL",
    "database-user": "PGEVENTBUS_DATABASE_USER",
    "database-password": "PGEVENTBUS_DATABASE_PASSWORD",
    "schema": "PGEVENTBUS_SCHEMA",
    "batch-size": "PGEVENTBUS_BATCH_SIZE",
    "lock-ttl-seconds": "PGEVENTBUS_LOCK_TTL_SECONDS",
    "timeout": "PGEVENTBUS_TIMEOUT",
}

// Pointers distinguish omitted keys from explicit zero values and empty passwords.
type tomlConfiguration struct {
    Timeout *string `toml:"timeout"`
    Database struct {
        URL *string `toml:"url"`
        User *string `toml:"user"`
        Password *string `toml:"password"`
        Schema *string `toml:"schema"`
    } `toml:"database"`
    Consumer struct {
        BatchSize *int64 `toml:"batch_size"`
        LockTTLSeconds *int64 `toml:"lock_ttl_seconds"`
    } `toml:"consumer"`
}

func loadConfiguration(c command) (configuration, error) {
    cfg := configuration{schema: "bus", batchSize: 100, ttl: 30, owner: os.Getenv("PGEVENTBUS_DB_OWNER")}
    values := map[string]setting{}
    path, err := configurationPath(c.options)
    if err != nil { return cfg, err }
    if path != "" {
        loaded, err := readTOML(path)
        if err != nil { return cfg, err }
        for key, value := range loaded { values[key] = setting{value, 1} }
    }
    for key, name := range environmentKeys {
        if value, exists := os.LookupEnv(name); exists { values[key] = setting{value, 2} }
    }
    for key, value := range c.options {
        if key != "config" { values[key] = setting{value, 3} }
    }
    cfg.url, err = normalizeURL(values["database-url"].value)
    if err != nil { return cfg, err }
    cfg.urlRank = values["database-url"].rank
    cfg.username, cfg.password = values["database-user"], values["database-password"]
    if v := strings.TrimSpace(values["schema"].value); v != "" { cfg.schema = v }
    if strings.ContainsRune(cfg.schema, 0) { return cfg, fmt.Errorf("schema cannot contain a NUL byte") }
    for key, target := range map[string]*int{"batch-size": &cfg.batchSize, "lock-ttl-seconds": &cfg.ttl} {
        if value, exists := values[key]; exists {
            n, err := strconv.ParseInt(value.value, 10, 32)
            if err != nil || n <= 0 { return cfg, fmt.Errorf("%s must be a positive int32", key) }
            *target = int(n)
        }
    }
    if value, exists := values["timeout"]; exists {
        cfg.timeout, err = time.ParseDuration(value.value)
        if err != nil || cfg.timeout < 0 { return cfg, fmt.Errorf("timeout must be 0 or a positive duration") }
    }
    if c.kind == "db init" {
        if len(c.args) == 1 { cfg.owner = c.args[0] }
        if !db.InstallIdentifier(cfg.owner) || !db.InstallIdentifier(cfg.schema) { return cfg, fmt.Errorf("initialization schema and owner must match [A-Za-z_][A-Za-z0-9_]*") }
    }
    return cfg, nil
}

// Filesystem failures are runtime failures; syntax/configuration failures use exit 2.
type fileError struct{}
func (*fileError) Error() string { return "cannot read configuration file" }

func configurationPath(options map[string]string) (string, error) {
    path, explicit := options["config"]
    if !explicit { path, explicit = os.LookupEnv("PGEVENTBUS_CONFIG") }
    if explicit {
        if path == "" { return "", fmt.Errorf("config path cannot be empty") }
        if !strings.EqualFold(filepath.Ext(path), ".toml") { return "", fmt.Errorf("config must be a TOML file") }
        return path, nil
    }
    const defaultPath = "pg-eventbus.toml"
    if _, err := os.Stat(defaultPath); err == nil { return defaultPath, nil } else if !os.IsNotExist(err) { return "", &fileError{} }
    return "", nil
}

func readTOML(path string) (map[string]string, error) {
    file, err := os.Open(path)
    if err != nil { return nil, &fileError{} }
    defer file.Close()
    content, err := io.ReadAll(io.LimitReader(file, (1<<20)+1))
    if err != nil { return nil, &fileError{} }
    if len(content) > 1<<20 { return nil, fmt.Errorf("configuration file exceeds 1 MiB") }
    var document tomlConfiguration
    metadata, err := toml.NewDecoder(bytes.NewReader(content)).Decode(&document)
    // Decoder errors may contain source lines with credentials; never print them.
    if err != nil { return nil, fmt.Errorf("invalid TOML configuration or incorrect value type") }
    if len(metadata.Undecoded()) != 0 { return nil, fmt.Errorf("unknown TOML configuration key") }
    // The decoder accepts case-insensitive struct fields; the public TOML schema does not.
    allowedKeys := map[string]bool{
        "timeout": true, "database": true, "consumer": true,
        "database.url": true, "database.user": true, "database.password": true,
        "database.schema": true, "consumer.batch_size": true, "consumer.lock_ttl_seconds": true,
    }
    for _, key := range metadata.Keys() {
        if !allowedKeys[key.String()] { return nil, fmt.Errorf("unknown TOML configuration key") }
    }
    values := map[string]string{}
    for key, value := range map[string]*string{
        "database-url": document.Database.URL, "database-user": document.Database.User,
        "database-password": document.Database.Password, "schema": document.Database.Schema,
        "timeout": document.Timeout,
    } {
        if value != nil { values[key] = *value }
    }
    if document.Consumer.BatchSize != nil { values["batch-size"] = strconv.FormatInt(*document.Consumer.BatchSize, 10) }
    if document.Consumer.LockTTLSeconds != nil { values["lock-ttl-seconds"] = strconv.FormatInt(*document.Consumer.LockTTLSeconds, 10) }
    return values, nil
}

func normalizeURL(raw string) (string, error) {
    parsed, err := url.Parse(raw)
    if err != nil || (parsed.Scheme != "postgresql" && parsed.Scheme != "postgres") || parsed.Host == "" { return "", fmt.Errorf("database URL must use postgresql:// or postgres:// with a host") }
    if parsed.Fragment != "" { return "", fmt.Errorf("database URL fragments are unsupported") }
    if _, err := url.ParseQuery(parsed.RawQuery); err != nil { return "", fmt.Errorf("invalid database URL query") }
    return parsed.String(), nil
}

func (cfg configuration) databaseOptions() db.Options {
    options := db.Options{URL: cfg.url, Schema: cfg.schema, BatchSize: cfg.batchSize, TTL: cfg.ttl}
    u, _ := url.Parse(cfg.url)
    inlineUser := u.User != nil && u.User.Username() != "" || u.Query().Has("user")
    inlinePassword := false
    if u.User != nil { _, inlinePassword = u.User.Password() }
    inlinePassword = inlinePassword || u.Query().Has("password")
    if cfg.username.rank != 0 && (!inlineUser || cfg.username.rank >= cfg.urlRank) { value := cfg.username.value; options.Username = &value }
    if cfg.password.rank != 0 && (!inlinePassword || cfg.password.rank >= cfg.urlRank) { value := cfg.password.value; options.Password = &value }
    return options
}
