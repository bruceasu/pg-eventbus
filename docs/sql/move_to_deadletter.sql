CREATE OR REPLACE FUNCTION bus.move_to_deadletter(
    p_original_id BIGINT,
    p_consumer_name TEXT,
    p_payload JSONB,
    p_error TEXT
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = bus, pg_catalog
AS $$
BEGIN
    INSERT INTO bus.event_deadletter(
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
