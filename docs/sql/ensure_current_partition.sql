CREATE OR REPLACE FUNCTION bus.ensure_current_partition()
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = bus, pg_catalog
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
          AND n.nspname = 'bus'
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
          AND n.nspname = 'bus'
    )
    INTO v_exists;

    IF NOT v_exists THEN
        PERFORM bus.create_month_partition(
            EXTRACT(YEAR FROM v_now)::INT,
            EXTRACT(MONTH FROM v_now)::INT
        );
    END IF;

    PERFORM pg_advisory_unlock(hashtext(v_part_name));
END;
$$;
