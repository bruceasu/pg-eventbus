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
