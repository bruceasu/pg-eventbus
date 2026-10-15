-- init.sql
-- Combined from docs/sql/*.sql in dependency order.
-- Change the schema in the two bootstrap statements below; no object prefixes need editing.
-- pg_catalog is searched implicitly first; pg_temp is explicitly last.
-- Functions capture this search_path at installation, independent of the caller.

BEGIN;

-- Source: docs/sql/bootstrap.sql
-- Select the target schema here before installing tables and functions.
-- Change bus in BOTH statements below (quote mixed-case or reserved names).
CREATE SCHEMA IF NOT EXISTS bus;
SET LOCAL search_path = bus, pg_temp;

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

-- Source: docs/sql/register_consumer.sql
CREATE OR REPLACE FUNCTION register_consumer(
    p_consumer_name TEXT
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path FROM CURRENT
AS $$
BEGIN
    INSERT INTO consumer_registry(consumer_name)
    VALUES (p_consumer_name)
    ON CONFLICT (consumer_name) DO NOTHING;
END;
$$;

-- Source: docs/sql/create_month_partition.sql
CREATE OR REPLACE FUNCTION create_month_partition(
    p_year INT,
    p_month INT
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path FROM CURRENT
AS $$
DECLARE
    v_start TIMESTAMPTZ;
    v_end TIMESTAMPTZ;
    v_name TEXT;
BEGIN
    v_start := make_timestamptz(p_year, p_month, 1, 0, 0, 0);
    v_end := v_start + interval '1 month';
    v_name := format('event_log_%s', to_char(v_start, 'YYYYMM'));

    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS %I
         PARTITION OF event_log
         FOR VALUES FROM (%L) TO (%L)',
        v_name, v_start, v_end
    );

    EXECUTE format(
        'CREATE INDEX IF NOT EXISTS %I_target_id
         ON %I (target_system, id)',
        v_name, v_name
    );
END;
$$;

-- Source: docs/sql/ensure_current_partition.sql
CREATE OR REPLACE FUNCTION ensure_current_partition()
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path FROM CURRENT
AS $$
DECLARE
    v_now TIMESTAMPTZ := date_trunc('month', now());
    v_part_name TEXT := 'event_log_' || to_char(v_now, 'YYYYMM');
    v_exists BOOLEAN;
BEGIN
    SELECT EXISTS (
        SELECT 1
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE c.relname = v_part_name
          AND n.nspname = current_schema()
    )
    INTO v_exists;

    IF v_exists THEN
        RETURN;
    END IF;

    PERFORM pg_advisory_lock(hashtext(v_part_name));

    SELECT EXISTS (
        SELECT 1
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE c.relname = v_part_name
          AND n.nspname = current_schema()
    )
    INTO v_exists;

    IF NOT v_exists THEN
        PERFORM create_month_partition(
            EXTRACT(YEAR FROM v_now)::INT,
            EXTRACT(MONTH FROM v_now)::INT
        );
    END IF;

    PERFORM pg_advisory_unlock(hashtext(v_part_name));
END;
$$;

-- Source: docs/sql/ensure_future_partitions.sql
CREATE OR REPLACE FUNCTION ensure_future_partitions(
    p_months_ahead INT DEFAULT 6
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path FROM CURRENT
AS $$
DECLARE
    v_base_date DATE := date_trunc('month', now())::DATE;
    v_i INT;
    v_target DATE;
    v_part_name TEXT;
    v_exists BOOLEAN;
BEGIN
    FOR v_i IN 1..p_months_ahead LOOP
        v_target := (v_base_date + (v_i || ' months')::interval)::DATE;
        v_part_name := 'event_log_' || to_char(v_target, 'YYYYMM');

        SELECT EXISTS (
            SELECT 1
            FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE c.relname = v_part_name
              AND n.nspname = current_schema()
        )
        INTO v_exists;

        IF NOT v_exists THEN
            PERFORM pg_advisory_lock(hashtext(v_part_name));

            SELECT EXISTS (
                SELECT 1
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE c.relname = v_part_name
                  AND n.nspname = current_schema()
            )
            INTO v_exists;

            IF NOT v_exists THEN
                PERFORM create_month_partition(
                    EXTRACT(YEAR FROM v_target)::INT,
                    EXTRACT(MONTH FROM v_target)::INT
                );
            END IF;

            PERFORM pg_advisory_unlock(hashtext(v_part_name));
        END IF;
    END LOOP;
END;
$$;

-- Source: docs/sql/acquire_consumer_lock.sql
CREATE OR REPLACE FUNCTION acquire_consumer_lock(
    p_consumer_name TEXT,
    p_instance_id TEXT,
    p_ttl_seconds INT DEFAULT 30
) RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path FROM CURRENT
AS $$
DECLARE
    v_updated INT;
BEGIN
    PERFORM register_consumer(p_consumer_name);

    UPDATE consumer_registry
       SET lock_owner = p_instance_id,
           lock_until = now() + make_interval(secs => p_ttl_seconds),
           updated_at = now()
     WHERE consumer_name = p_consumer_name
       AND (lock_until IS NULL OR lock_until < now());

    GET DIAGNOSTICS v_updated = ROW_COUNT;
    RETURN v_updated = 1;
END;
$$;

-- Source: docs/sql/renew_consumer_lock.sql
CREATE OR REPLACE FUNCTION renew_consumer_lock(
    p_consumer_name TEXT,
    p_instance_id TEXT,
    p_ttl_seconds INT DEFAULT 30
) RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path FROM CURRENT
AS $$
DECLARE
    v_updated INT;
BEGIN
    UPDATE consumer_registry
       SET lock_until = now() + make_interval(secs => p_ttl_seconds),
           updated_at = now()
     WHERE consumer_name = p_consumer_name
       AND lock_owner = p_instance_id
       AND lock_until >= now();

    GET DIAGNOSTICS v_updated = ROW_COUNT;
    RETURN v_updated = 1;
END;
$$;

-- Source: docs/sql/release_consumer_lock.sql
CREATE OR REPLACE FUNCTION release_consumer_lock(
    p_consumer_name TEXT,
    p_instance_id TEXT
) RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path FROM CURRENT
AS $$
DECLARE
    v_updated INT;
BEGIN
    UPDATE consumer_registry
       SET lock_owner = NULL,
           lock_until = NULL,
           updated_at = now()
     WHERE consumer_name = p_consumer_name
       AND lock_owner = p_instance_id;

    GET DIAGNOSTICS v_updated = ROW_COUNT;
    RETURN v_updated = 1;
END;
$$;

-- Source: docs/sql/commit_consumer_offset.sql
CREATE OR REPLACE FUNCTION commit_consumer_offset(
    p_consumer_name TEXT,
    p_instance_id TEXT,
    p_new_last_id BIGINT
) RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path FROM CURRENT
AS $$
DECLARE
    v_updated INT;
BEGIN
    UPDATE consumer_registry
       SET last_event_id = GREATEST(last_event_id, p_new_last_id),
           updated_at = now()
     WHERE consumer_name = p_consumer_name
       AND lock_owner = p_instance_id
       AND lock_until >= now();

    GET DIAGNOSTICS v_updated = ROW_COUNT;
    RETURN v_updated = 1;
END;
$$;

-- Source: docs/sql/init_consumer_at_time.sql
CREATE OR REPLACE FUNCTION init_consumer_at_time(
    p_consumer_name TEXT,
    p_start_time TIMESTAMPTZ
) RETURNS BIGINT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path FROM CURRENT
AS $$
DECLARE
    v_id BIGINT;
BEGIN
    PERFORM register_consumer(p_consumer_name);

    SELECT COALESCE(MAX(id), 0)
      INTO v_id
      FROM event_log
     WHERE created_at <= p_start_time;

    UPDATE consumer_registry
       SET last_event_id = v_id,
           updated_at = now()
     WHERE consumer_name = p_consumer_name;

    RETURN v_id;
END;
$$;

-- Source: docs/sql/publish_event.sql
CREATE OR REPLACE FUNCTION publish_event(
    p_source_system TEXT,
    p_target_system TEXT,
    p_event_type TEXT,
    p_payload JSONB
) RETURNS BIGINT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path FROM CURRENT
AS $$
DECLARE
    v_id BIGINT;
BEGIN
    PERFORM ensure_current_partition();

    INSERT INTO event_log(source_system, target_system, event_type, payload)
    VALUES (p_source_system, p_target_system, p_event_type, p_payload)
    RETURNING id INTO v_id;

    RETURN v_id;
END;
$$;

-- Source: docs/sql/poll_events.sql
CREATE OR REPLACE FUNCTION poll_events(
    p_consumer_name TEXT,
    p_limit INT DEFAULT 100
) RETURNS TABLE(
    id BIGINT,
    source_system TEXT,
    target_system TEXT,
    event_type TEXT,
    payload JSONB,
    created_at TIMESTAMPTZ
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path FROM CURRENT
AS $$
DECLARE
    v_last BIGINT;
BEGIN
    PERFORM register_consumer(p_consumer_name);

    SELECT last_event_id
      INTO v_last
      FROM consumer_registry
     WHERE consumer_name = p_consumer_name;

    RETURN QUERY
    SELECT e.id,
           e.source_system,
           e.target_system,
           e.event_type,
           e.payload,
           e.created_at
      FROM event_log e
     WHERE e.id > v_last
       AND (e.target_system = p_consumer_name OR e.target_system IS NULL)
     ORDER BY e.id
     LIMIT p_limit;
END;
$$;

-- Source: docs/sql/poll_retry.sql
CREATE OR REPLACE FUNCTION poll_retry(
    p_consumer_name TEXT,
    p_limit INT DEFAULT 50
) RETURNS TABLE(
    retry_id BIGINT,
    original_id BIGINT,
    payload JSONB,
    retry_count INT
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path FROM CURRENT
AS $$
BEGIN
    RETURN QUERY
    SELECT r.retry_id,
           r.original_id,
           r.payload,
           r.retry_count
      FROM event_retry r
     WHERE r.consumer_name = p_consumer_name
       AND r.next_retry_at <= now()
     ORDER BY r.next_retry_at
     LIMIT p_limit
     FOR UPDATE SKIP LOCKED;
END;
$$;

-- Source: docs/sql/poll_deadletter.sql
CREATE OR REPLACE FUNCTION poll_deadletter(
    p_consumer_name TEXT,
    p_limit INT DEFAULT 50
) RETURNS TABLE(
    dead_id BIGINT,
    original_id BIGINT,
    payload JSONB,
    error_message TEXT,
    failed_at TIMESTAMPTZ
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path FROM CURRENT
AS $$
BEGIN
    RETURN QUERY
    SELECT d.dead_id,
           d.original_id,
           d.payload,
           d.error_message,
           d.failed_at
      FROM event_deadletter d
     WHERE d.consumer_name = p_consumer_name
     ORDER BY d.failed_at DESC, d.dead_id DESC
     LIMIT p_limit;
END;
$$;

-- Source: docs/sql/schedule_retry.sql
CREATE OR REPLACE FUNCTION schedule_retry(
    p_original_id BIGINT,
    p_consumer_name TEXT,
    p_payload JSONB,
    p_retry_count INT
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path FROM CURRENT
AS $$
DECLARE
    v_delay INTERVAL;
BEGIN
    v_delay := LEAST(
        (2 ^ p_retry_count) * interval '1 second',
        interval '1 hour'
    );

    INSERT INTO event_retry(
        original_id,
        consumer_name,
        payload,
        retry_count,
        next_retry_at
    )
    VALUES (
        p_original_id,
        p_consumer_name,
        p_payload,
        p_retry_count + 1,
        now() + v_delay
    );
END;
$$;

-- Source: docs/sql/move_to_deadletter.sql
CREATE OR REPLACE FUNCTION move_to_deadletter(
    p_original_id BIGINT,
    p_consumer_name TEXT,
    p_payload JSONB,
    p_error TEXT
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path FROM CURRENT
AS $$
BEGIN
    INSERT INTO event_deadletter(
        original_id,
        consumer_name,
        payload,
        error_message
    )
    VALUES (
        p_original_id,
        p_consumer_name,
        p_payload,
        p_error
    );
END;
$$;

-- Source: docs/sql/delete_retry.sql
CREATE OR REPLACE FUNCTION delete_retry(
    p_retry_id BIGINT
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path FROM CURRENT
AS $$
BEGIN
    DELETE FROM event_retry
     WHERE retry_id = p_retry_id;
END;
$$;

-- Source: docs/sql/cleanup_consumed.sql
CREATE OR REPLACE FUNCTION cleanup_consumed(
    p_batch_size INT DEFAULT 10000
) RETURNS INT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path FROM CURRENT
AS $$
DECLARE
    v_min BIGINT;
    v_deleted INT;
BEGIN
    SELECT MIN(last_event_id) INTO v_min
      FROM consumer_registry;

    IF v_min IS NULL THEN
        RETURN 0;
    END IF;

    WITH to_delete AS (
        SELECT ctid
          FROM event_log
         WHERE id < v_min
         ORDER BY id
         LIMIT p_batch_size
    )
    DELETE FROM event_log e
    USING to_delete d
    WHERE e.ctid = d.ctid;

    GET DIAGNOSTICS v_deleted = ROW_COUNT;
    RETURN v_deleted;
END;
$$;

-- Source: docs/sql/cleanup_older_than.sql
CREATE OR REPLACE FUNCTION cleanup_older_than(
    p_older_than INTERVAL,
    p_batch_size INT DEFAULT 10000
) RETURNS INT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path FROM CURRENT
AS $$
DECLARE
    v_deleted INT;
BEGIN
    WITH to_delete AS (
        SELECT ctid
          FROM event_log
         WHERE created_at < now() - p_older_than
         ORDER BY created_at
         LIMIT p_batch_size
    )
    DELETE FROM event_log e
    USING to_delete d
    WHERE e.ctid = d.ctid;

    GET DIAGNOSTICS v_deleted = ROW_COUNT;
    RETURN v_deleted;
END;
$$;

-- Source: docs/sql/drop_partition_older_than.sql
CREATE OR REPLACE FUNCTION drop_partition_older_than(
    p_keep_months INT
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path FROM CURRENT
AS $$
DECLARE
    v_cutoff DATE;
    v_part RECORD;
BEGIN
    v_cutoff := (date_trunc('month', now()) - (p_keep_months || ' months')::interval)::DATE;

    FOR v_part IN
        SELECT inhrelid::regclass AS partition_name, child.relname AS partition_basename
        FROM pg_inherits
        JOIN pg_class child ON pg_inherits.inhrelid = child.oid
        JOIN pg_class parent ON pg_inherits.inhparent = parent.oid
        WHERE parent.relname = 'event_log'
          AND parent.relnamespace = current_schema()::regnamespace
    LOOP
        IF v_part.partition_basename < 'event_log_' || to_char(v_cutoff, 'YYYYMM') THEN
            EXECUTE format('DROP TABLE IF EXISTS %s CASCADE', v_part.partition_name);
        END IF;
    END LOOP;
END;
$$;

-- Source: docs/sql/maintenance.sql
CREATE OR REPLACE FUNCTION maintenance()
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path FROM CURRENT
AS $$
BEGIN
    PERFORM ensure_future_partitions(6);
    PERFORM drop_partition_older_than(3);
END;
$$;

-- Source: docs/sql/grant_access.sql
-- Grant access in the schema selected by bootstrap.sql / init.sql.
-- Replace app_role with the existing application role before manual execution.
DO $$
DECLARE
    v_schema TEXT := current_schema();
BEGIN
    EXECUTE format('REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA %I FROM app_role', v_schema);
    EXECUTE format('REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA %I FROM app_role', v_schema);
    EXECUTE format('GRANT USAGE ON SCHEMA %I TO app_role', v_schema);
    EXECUTE format('GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA %I TO app_role', v_schema);
    EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA %I GRANT EXECUTE ON FUNCTIONS TO app_role', v_schema);
END;
$$;

COMMIT;
