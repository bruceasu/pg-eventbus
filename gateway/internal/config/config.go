// Package config owns the gateway's environment contract; it never logs values.
package config

import (
	"fmt"
	"net"
	"net/url"
	"os"
	"strconv"
	"strings"
	"time"
)

type Config struct {
	DatabaseURL string
	User, Password *string
	Schema, ListenAddr string
	BatchSize, LockTTL int32
	RequestTimeout time.Duration
}

func env(primary, legacy, fallback string) string {
	if value, ok := os.LookupEnv(primary); ok { return value }
	if legacy != "" { if value, ok := os.LookupEnv(legacy); ok { return value } }
	return fallback
}

func credential(primary, legacy string) *string {
	if value, ok := os.LookupEnv(primary); ok { return &value }
	if value, ok := os.LookupEnv(legacy); ok { return &value }
	return nil
}

func positive(primary, legacy string, fallback int32) (int32, error) {
	value, err := strconv.ParseInt(env(primary, legacy, strconv.FormatInt(int64(fallback), 10)), 10, 32)
	if err != nil || value <= 0 { return 0, fmt.Errorf("%s must be a positive int32", primary) }
	return int32(value), nil
}

func Load() (Config, error) {
	c := Config{Schema: strings.TrimSpace(env("PGEVENTBUS_SCHEMA", "BUS_SCHEMA", "bus")),
		User: credential("PGEVENTBUS_DATABASE_USER", "SPRING_DATASOURCE_USERNAME"),
		Password: credential("PGEVENTBUS_DATABASE_PASSWORD", "SPRING_DATASOURCE_PASSWORD")}
	if c.Schema == "" { c.Schema = "bus" }
	if strings.ContainsRune(c.Schema, 0) { return c, fmt.Errorf("PGEVENTBUS_SCHEMA contains a NUL character") }
	var native bool
	c.DatabaseURL, native = os.LookupEnv("PGEVENTBUS_DATABASE_URL")
	if !native { c.DatabaseURL = strings.TrimPrefix(os.Getenv("SPRING_DATASOURCE_URL"), "jdbc:") }
	u, err := url.Parse(c.DatabaseURL)
	if err != nil || u == nil || (u.Scheme != "postgres" && u.Scheme != "postgresql") || u.Hostname() == "" {
		return c, fmt.Errorf("PGEVENTBUS_DATABASE_URL must be a PostgreSQL URL with a host")
	}
	if c.BatchSize, err = positive("PGEVENTBUS_BATCH_SIZE", "BUS_BATCH_SIZE", 100); err != nil { return c, err }
	if c.LockTTL, err = positive("PGEVENTBUS_LOCK_TTL_SECONDS", "BUS_LOCK_TTL_SECONDS", 30); err != nil { return c, err }
	c.RequestTimeout, err = time.ParseDuration(env("PGEVENTBUS_REQUEST_TIMEOUT", "", "30s"))
	// Reserve headroom for the HTTP write timeout to avoid duration overflow.
	if err != nil || c.RequestTimeout <= 0 || c.RequestTimeout > 24*time.Hour {
		return c, fmt.Errorf("PGEVENTBUS_REQUEST_TIMEOUT must be positive and at most 24h")
	}
	if addr, ok := os.LookupEnv("PGEVENTBUS_LISTEN_ADDR"); ok {
		c.ListenAddr = addr
	} else {
		c.ListenAddr = ":" + env("SERVER_PORT", "", "8080")
	}
	_, port, err := net.SplitHostPort(c.ListenAddr)
	if err != nil { return c, fmt.Errorf("PGEVENTBUS_LISTEN_ADDR must be host:port") }
	n, err := strconv.ParseUint(port, 10, 16)
	if err != nil || n == 0 { return c, fmt.Errorf("listen port must be in 1..65535") }
	return c, nil
}
