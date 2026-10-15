CREATE OR REPLACE FUNCTION poll_deadletter(
    p_consumer_name TEXT,
    p_limit INT DEFAULT 50
) RETURNS TABLE(
    dead_id BIGINT,
    original_id BIGINT,
    payload JSONB,
    error_message TEXT,
    failed_at TIMESTAMPTZ
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path FROM CURRENT
AS $$
BEGIN
    RETURN QUERY
    SELECT d.dead_id,
           d.original_id,
           d.payload,
           d.error_message,
           d.failed_at
      FROM event_deadletter d
     WHERE d.consumer_name = p_consumer_name
     ORDER BY d.failed_at DESC, d.dead_id DESC
     LIMIT p_limit;
END;
$$;
