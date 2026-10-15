CREATE OR REPLACE FUNCTION maintenance()
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path FROM CURRENT
AS $$
BEGIN
    PERFORM ensure_future_partitions(6);
    PERFORM drop_partition_older_than(3);
END;
$$;
