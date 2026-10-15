package app

import (
	"bufio"
	"bytes"
	"fmt"
	"io"
	"net/url"
	"os"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"time"

	"github.com/bruceasu/pg-eventbus/cli/internal/db"
	"gopkg.in/yaml.v3"
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
	"spring.datasource.url": "SPRING_DATASOURCE_URL",
	"spring.datasource.username": "SPRING_DATASOURCE_USERNAME",
	"spring.datasource.password": "SPRING_DATASOURCE_PASSWORD",
	"spring.datasource.driver-class-name": "SPRING_DATASOURCE_DRIVER_CLASS_NAME",
	"bus.schema": "BUS_SCHEMA", "bus.batch-size": "BUS_BATCH_SIZE",
	"bus.lock-ttl-seconds": "BUS_LOCK_TTL_SECONDS",
}

func loadConfiguration(c command) (configuration, error) {
	cfg := configuration{schema: "bus", batchSize: 100, ttl: 30, owner: os.Getenv("PGEVENTBUS_DB_OWNER")}
	if err := rejectUnsupportedEnvironment(); err != nil { return cfg, err }
	values := map[string]setting{}
	path, err := configurationPath(c.options)
	if err != nil { return cfg, err }
	if path != "" {
		file, err := os.Open(path)
		if err != nil { return cfg, &fileError{} }
		defer file.Close()
		content, err := io.ReadAll(io.LimitReader(file, (1<<20)+1))
		if err != nil { return cfg, &fileError{} }
		if len(content) > 1<<20 { return cfg, fmt.Errorf("configuration file exceeds 1 MiB") }
		var loaded map[string]string
		if strings.EqualFold(filepath.Ext(path), ".properties") { loaded, err = readProperties(bytes.NewReader(content)) } else { loaded, err = readYAML(bytes.NewReader(content)) }
		if err != nil { return cfg, err }
		for key, value := range loaded {
			if !optionKeys[key] || key == "config" || key == "timeout" { return cfg, fmt.Errorf("unsupported configuration key %s", key) }
			if strings.Contains(value, "${") { return cfg, fmt.Errorf("Spring placeholder expansion is unsupported") }
			values[key] = setting{value, 1}
		}
	}
	for key, name := range environmentKeys {
		if value, exists := os.LookupEnv(name); exists { values[key] = setting{value, 2} }
	}
	for key, value := range c.options {
		if key != "config" && key != "timeout" { values[key] = setting{value, 3} }
	}
	if driver := values["spring.datasource.driver-class-name"]; driver.rank != 0 && driver.value != "org.postgresql.Driver" { return cfg, fmt.Errorf("only PostgreSQL configuration is supported") }
	cfg.url, err = normalizeURL(values["spring.datasource.url"].value)
	if err != nil { return cfg, err }
	cfg.urlRank = values["spring.datasource.url"].rank
	cfg.username, cfg.password = values["spring.datasource.username"], values["spring.datasource.password"]
	if v := strings.TrimSpace(values["bus.schema"].value); v != "" { cfg.schema = v }
	if strings.ContainsRune(cfg.schema, 0) { return cfg, fmt.Errorf("schema cannot contain a NUL byte") }
	for key, target := range map[string]*int{"bus.batch-size": &cfg.batchSize, "bus.lock-ttl-seconds": &cfg.ttl} {
		if value, exists := values[key]; exists {
			n, err := strconv.ParseInt(value.value, 10, 32)
			if err != nil || n <= 0 { return cfg, fmt.Errorf("%s must be a positive int32", key) }
			*target = int(n)
		}
	}
	if value, exists := c.options["timeout"]; exists {
		cfg.timeout, err = time.ParseDuration(value)
		if err != nil || cfg.timeout < 0 { return cfg, fmt.Errorf("timeout must be 0 or a positive duration") }
	}
	if c.kind == "db init" {
		if len(c.args) == 1 { cfg.owner = c.args[0] }
		if !db.InstallIdentifier(cfg.owner) || !db.InstallIdentifier(cfg.schema) { return cfg, fmt.Errorf("initialization schema and owner must match [A-Za-z_][A-Za-z0-9_]*") }
	}
	return cfg, nil
}

// An inherited Spring control can select a different database/configuration in Java.
// Reject explicit settings, including empty values, rather than silently ignoring them.
func rejectUnsupportedEnvironment() error {
	allowed := map[string]bool{}
	for _, name := range environmentKeys { allowed[name] = true }
	for _, entry := range os.Environ() {
		name, _, _ := strings.Cut(entry, "=")
		if runtime.GOOS == "windows" { name = strings.ToUpper(name) }
		reserved := strings.HasPrefix(name, "SPRING_PROFILES_") ||
			strings.HasPrefix(name, "SPRING_CONFIG_") || name == "SPRING_APPLICATION_JSON" ||
			strings.HasPrefix(name, "SPRING_DATASOURCE_")
		if reserved && !allowed[name] { return fmt.Errorf("unsupported Spring environment control; remove profile, config-location or unsupported datasource settings") }
	}
	return nil
}

// Filesystem failures are runtime failures; syntax/configuration failures use exit 2.
type fileError struct{}
func (*fileError) Error() string { return "cannot read configuration file" }

func configurationPath(options map[string]string) (string, error) {
	if path, exists := options["config"]; exists {
		if path == "" { return "", fmt.Errorf("config path cannot be empty") }
		ext := strings.ToLower(filepath.Ext(path))
		if ext != ".yml" && ext != ".yaml" && ext != ".properties" { return "", fmt.Errorf("config must be YAML or properties") }
		return path, nil
	}
	var found []string
	for _, path := range []string{"application.yml", "application.yaml", "application.properties"} {
		_, err := os.Stat(path)
		if err == nil { found = append(found, path) } else if !os.IsNotExist(err) { return "", &fileError{} }
	}
	if len(found) > 1 { return "", fmt.Errorf("multiple configuration files: select one with --config") }
	if len(found) == 1 { return found[0], nil }
	return "", nil
}

func readYAML(reader io.Reader) (map[string]string, error) {
	decoder := yaml.NewDecoder(io.LimitReader(reader, 1<<20))
	var document yaml.Node
	if err := decoder.Decode(&document); err != nil && err != io.EOF { return nil, fmt.Errorf("invalid YAML configuration") }
	var extra yaml.Node
	if err := decoder.Decode(&extra); err != io.EOF { return nil, fmt.Errorf("multiple YAML documents or invalid YAML are unsupported") }
	values := map[string]string{}
	if len(document.Content) == 0 { return values, nil }
	var flatten func(*yaml.Node, string) error
	flatten = func(node *yaml.Node, prefix string) error {
		if node.Kind != yaml.MappingNode { return fmt.Errorf("configuration root and namespaces must be mappings") }
		seen := map[string]bool{}
		for i := 0; i < len(node.Content); i += 2 {
			key, value := node.Content[i], node.Content[i+1]
			if key.Kind != yaml.ScalarNode || seen[key.Value] { return fmt.Errorf("invalid or duplicate YAML key") }
			seen[key.Value] = true
			name := prefix + key.Value
			if value.Kind == yaml.MappingNode {
				knownNamespace := false
				for supported := range optionKeys { if strings.HasPrefix(supported, name+".") { knownNamespace = true; break } }
				if !knownNamespace { return fmt.Errorf("unsupported YAML configuration namespace") }
				if err := flatten(value, name+"."); err != nil { return err }
			} else if value.Kind == yaml.ScalarNode {
				if _, exists := values[name]; exists { return fmt.Errorf("duplicate configuration key") }
				values[name] = value.Value
			} else { return fmt.Errorf("YAML sequences and aliases are unsupported") }
		}
		return nil
	}
	return values, flatten(document.Content[0], "")
}

func readProperties(reader io.Reader) (map[string]string, error) {
	values := map[string]string{}
	scanner := bufio.NewScanner(io.LimitReader(reader, 1<<20))
	for scanner.Scan() {
		line := strings.TrimLeft(scanner.Text(), " \t")
		if line == "" || strings.HasPrefix(line, "#") || strings.HasPrefix(line, "!") { continue }
		index := strings.IndexAny(line, "=: \t")
		if index < 1 { return nil, fmt.Errorf("properties require key=value entries") }
		key := line[:index]
		value := strings.TrimLeft(line[index:], " \t")
		if strings.HasPrefix(value, "=") || strings.HasPrefix(value, ":") { value = value[1:] }
		value = strings.TrimLeft(value, " \t")
		if strings.Contains(value, "\\") { return nil, fmt.Errorf("properties escapes and continuation are unsupported; use YAML for such values") }
		if _, exists := values[key]; exists { return nil, fmt.Errorf("duplicate properties key") }
		values[key] = value
	}
	if err := scanner.Err(); err != nil { return nil, &fileError{} }
	return values, nil
}

func normalizeURL(raw string) (string, error) {
	jdbc := strings.HasPrefix(raw, "jdbc:postgresql://")
	if jdbc { raw = strings.TrimPrefix(raw, "jdbc:") }
	parsed, err := url.Parse(raw)
	if err != nil || (parsed.Scheme != "postgresql" && parsed.Scheme != "postgres") || parsed.Host == "" { return "", fmt.Errorf("a valid PostgreSQL datasource URL is required") }
	if parsed.Fragment != "" { return "", fmt.Errorf("datasource URL fragments are unsupported") }
	query, err := url.ParseQuery(parsed.RawQuery)
	if err != nil { return "", fmt.Errorf("invalid datasource URL query") }
	if jdbc {
		allowed := map[string]bool{"sslmode":true,"sslrootcert":true,"sslcert":true,"sslkey":true,"application_name":true,"connectTimeout":true,"user":true,"password":true,"options":true}
		for key := range query { if !allowed[key] { return "", fmt.Errorf("unsupported JDBC connection parameter; use documented PostgreSQL URL parameters") } }
		if values, exists := query["connectTimeout"]; exists {
			if len(values) != 1 { return "", fmt.Errorf("duplicate connectTimeout parameter") }
			n, err := strconv.ParseInt(values[0], 10, 32)
			if err != nil || n < 0 { return "", fmt.Errorf("connectTimeout must be nonnegative seconds") }
			query.Set("connect_timeout", values[0]); query.Del("connectTimeout")
		}
	}
	parsed.RawQuery = query.Encode()
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
