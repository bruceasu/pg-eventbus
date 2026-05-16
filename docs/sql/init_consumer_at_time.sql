CREATE OR REPLACE FUNCTION bus.init_consumer_at_time(
    p_consumer_name TEXT,
    p_start_time TIMESTAMPTZ
) RETURNS BIGINT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = bus, pg_catalog
AS $$
DECLARE
    v_id BIGINT;
BEGIN
    PERFORM bus.register_consumer(p_consumer_name);

    SELECT COALESCE(MAX(id), 0)
      INTO v_id
      FROM bus.event_log
     WHERE created_at <= p_start_time;

    UPDATE bus.consumer_registry
       SET last_event_id = v_id,
           updated_at = now()
     WHERE consumer_name = p_consumer_name;

    RETURN v_id;
END;
$$;
