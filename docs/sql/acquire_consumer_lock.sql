CREATE OR REPLACE FUNCTION bus.acquire_consumer_lock(
    p_consumer_name TEXT,
    p_instance_id TEXT,
    p_ttl_seconds INT DEFAULT 30
) RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = bus, pg_catalog
AS $$
DECLARE
    v_updated INT;
BEGIN
    PERFORM bus.register_consumer(p_consumer_name);

    UPDATE bus.consumer_registry
       SET lock_owner = p_instance_id,
           lock_until = now() + make_interval(secs => p_ttl_seconds),
           updated_at = now()
     WHERE consumer_name = p_consumer_name
       AND (lock_until IS NULL OR lock_until < now());

    GET DIAGNOSTICS v_updated = ROW_COUNT;
    RETURN v_updated = 1;
END;
$$;
