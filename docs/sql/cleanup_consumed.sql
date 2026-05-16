CREATE OR REPLACE FUNCTION bus.cleanup_consumed(
    p_batch_size INT DEFAULT 10000
) RETURNS INT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = bus, pg_catalog
AS $$
DECLARE
    v_min BIGINT;
    v_deleted INT;
BEGIN
    SELECT MIN(last_event_id) INTO v_min
      FROM bus.consumer_registry;

    IF v_min IS NULL THEN
        RETURN 0;
    END IF;

    WITH to_delete AS (
        SELECT ctid
          FROM bus.event_log
         WHERE id < v_min
         ORDER BY id
         LIMIT p_batch_size
    )
    DELETE FROM bus.event_log e
    USING to_delete d
    WHERE e.ctid = d.ctid;

    GET DIAGNOSTICS v_deleted = ROW_COUNT;
    RETURN v_deleted;
END;
$$;
