CREATE OR REPLACE FUNCTION bus.poll_events(
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
SET search_path = bus, pg_catalog
AS $$
DECLARE
    v_last BIGINT;
BEGIN
    PERFORM bus.register_consumer(p_consumer_name);

    SELECT last_event_id
      INTO v_last
      FROM bus.consumer_registry
     WHERE consumer_name = p_consumer_name;

    RETURN QUERY
    SELECT e.id,
           e.source_system,
           e.target_system,
           e.event_type,
           e.payload,
           e.created_at
      FROM bus.event_log e
     WHERE e.id > v_last
       AND (e.target_system = p_consumer_name OR e.target_system IS NULL)
     ORDER BY e.id
     LIMIT p_limit;
END;
$$;
