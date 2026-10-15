CREATE OR REPLACE FUNCTION schedule_retry(
    p_original_id BIGINT,
    p_consumer_name TEXT,
    p_payload JSONB,
    p_retry_count INT
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path FROM CURRENT
AS $$
DECLARE
    v_delay INTERVAL;
BEGIN
    v_delay := LEAST(
        (2 ^ p_retry_count) * interval '1 second',
        interval '1 hour'
    );

    INSERT INTO event_retry(
        original_id,
        consumer_name,
        payload,
        retry_count,
        next_retry_at
    )
    VALUES (
        p_original_id,
        p_consumer_name,
        p_payload,
        p_retry_count + 1,
        now() + v_delay
    );
END;
$$;
