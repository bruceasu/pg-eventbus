CREATE OR REPLACE FUNCTION bus.renew_consumer_lock(
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
    UPDATE bus.consumer_registry
       SET lock_until = now() + make_interval(secs => p_ttl_seconds),
           updated_at = now()
     WHERE consumer_name = p_consumer_name
       AND lock_owner = p_instance_id
       AND lock_until >= now();

    GET DIAGNOSTICS v_updated = ROW_COUNT;
    RETURN v_updated = 1;
END;
$$;
