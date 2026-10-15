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
