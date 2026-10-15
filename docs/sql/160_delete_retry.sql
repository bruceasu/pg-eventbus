CREATE OR REPLACE FUNCTION delete_retry(
    p_retry_id BIGINT
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path FROM CURRENT
AS $$
BEGIN
    DELETE FROM event_retry
     WHERE retry_id = p_retry_id;
END;
$$;
