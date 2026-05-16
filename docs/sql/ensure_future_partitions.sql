CREATE OR REPLACE FUNCTION bus.ensure_future_partitions(
    p_months_ahead INT DEFAULT 6
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = bus, pg_catalog
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
              AND n.nspname = 'bus'
        )
        INTO v_exists;

        IF NOT v_exists THEN
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
                    EXTRACT(YEAR FROM v_target)::INT,
                    EXTRACT(MONTH FROM v_target)::INT
                );
            END IF;

            PERFORM pg_advisory_unlock(hashtext(v_part_name));
        END IF;
    END LOOP;
END;
$$;
