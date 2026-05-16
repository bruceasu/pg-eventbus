CREATE OR REPLACE FUNCTION bus.delete_retry(
    p_retry_id BIGINT
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = bus, pg_catalog
AS $$
BEGIN
    DELETE FROM bus.event_retry
     WHERE retry_id = p_retry_id;
END;
$$;
