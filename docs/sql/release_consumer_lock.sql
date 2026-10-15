CREATE OR REPLACE FUNCTION release_consumer_lock(
    p_consumer_name TEXT,
    p_instance_id TEXT
) RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path FROM CURRENT
AS $$
DECLARE
    v_updated INT;
BEGIN
    UPDATE consumer_registry
       SET lock_owner = NULL,
           lock_until = NULL,
           updated_at = now()
     WHERE consumer_name = p_consumer_name
       AND lock_owner = p_instance_id;

    GET DIAGNOSTICS v_updated = ROW_COUNT;
    RETURN v_updated = 1;
END;
$$;
