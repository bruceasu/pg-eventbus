CREATE OR REPLACE FUNCTION bus.poll_retry(
    p_consumer_name TEXT,
    p_limit INT DEFAULT 50
) RETURNS TABLE(
    retry_id BIGINT,
    original_id BIGINT,
    payload JSONB,
    retry_count INT
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = bus, pg_catalog
AS $$
BEGIN
    RETURN QUERY
    SELECT r.retry_id,
           r.original_id,
           r.payload,
           r.retry_count
      FROM bus.event_retry r
     WHERE r.consumer_name = p_consumer_name
       AND r.next_retry_at <= now()
     ORDER BY r.next_retry_at
     LIMIT p_limit
     FOR UPDATE SKIP LOCKED;
END;
$$;
