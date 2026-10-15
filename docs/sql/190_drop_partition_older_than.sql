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
