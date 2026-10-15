CREATE OR REPLACE FUNCTION acquire_consumer_lock(
    p_consumer_name TEXT,
    p_instance_id TEXT,
    p_ttl_seconds INT DEFAULT 30
) RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path FROM CURRENT
AS $$
DECLARE
    v_updated INT;
BEGIN
    PERFORM register_consumer(p_consumer_name);

    UPDATE consumer_registry
       SET lock_owner = p_instance_id,
           lock_until = now() + make_interval(secs => p_ttl_seconds),
           updated_at = now()
     WHERE consumer_name = p_consumer_name
       AND (lock_until IS NULL OR lock_until < now());

    GET DIAGNOSTICS v_updated = ROW_COUNT;
    RETURN v_updated = 1;
END;
$$;
