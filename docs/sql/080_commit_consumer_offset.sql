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
