CREATE OR REPLACE FUNCTION cleanup_older_than(
    p_older_than INTERVAL,
    p_batch_size INT DEFAULT 10000
) RETURNS INT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path FROM CURRENT
AS $$
DECLARE
    v_deleted INT;
BEGIN
    WITH to_delete AS (
        SELECT ctid
          FROM event_log
         WHERE created_at < now() - p_older_than
         ORDER BY created_at
         LIMIT p_batch_size
    )
    DELETE FROM event_log e
    USING to_delete d
    WHERE e.ctid = d.ctid;

    GET DIAGNOSTICS v_deleted = ROW_COUNT;
    RETURN v_deleted;
END;
$$;
