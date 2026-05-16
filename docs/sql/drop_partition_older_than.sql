CREATE OR REPLACE FUNCTION bus.drop_partition_older_than(
    p_keep_months INT
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = bus, pg_catalog
AS $$
DECLARE
    v_cutoff DATE;
    v_part RECORD;
BEGIN
    v_cutoff := (date_trunc('month', now()) - (p_keep_months || ' months')::interval)::DATE;

    FOR v_part IN
        SELECT inhrelid::regclass AS partition_name
        FROM pg_inherits
        JOIN pg_class parent ON pg_inherits.inhparent = parent.oid
        WHERE parent.relname = 'event_log'
          AND parent.relnamespace = 'bus'::regnamespace
    LOOP
        IF v_part.partition_name::TEXT < 'bus.event_log_' || to_char(v_cutoff, 'YYYYMM') THEN
            EXECUTE format('DROP TABLE IF EXISTS %s CASCADE', v_part.partition_name);
        END IF;
    END LOOP;
END;
$$;
