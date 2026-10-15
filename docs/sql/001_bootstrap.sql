-- Select the target schema here before installing tables and functions.
-- Change bus in BOTH statements below (quote mixed-case or reserved names).
-- Run all docs/sql scripts in dependency order in this same session.
CREATE SCHEMA IF NOT EXISTS bus;
SET search_path = bus, pg_temp;

CREATE TABLE IF NOT EXISTS event_log (
    id BIGSERIAL,
    source_system TEXT NOT NULL,
    target_system TEXT,
    event_type TEXT NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE TABLE IF NOT EXISTS consumer_registry (
    consumer_name TEXT PRIMARY KEY,
    last_event_id BIGINT NOT NULL DEFAULT 0,
    registered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    lock_owner TEXT,
    lock_until TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS event_retry (
    retry_id BIGSERIAL PRIMARY KEY,
    original_id BIGINT NOT NULL,
    consumer_name TEXT NOT NULL,
    payload JSONB NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS event_deadletter (
    dead_id BIGSERIAL PRIMARY KEY,
    original_id BIGINT,
    consumer_name TEXT,
    payload JSONB,
    error_message TEXT,
    failed_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_retry_due
    ON event_retry(next_retry_at);

CREATE INDEX IF NOT EXISTS idx_retry_consumer
    ON event_retry(consumer_name);
