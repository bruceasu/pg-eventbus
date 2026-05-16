CREATE OR REPLACE FUNCTION bus.maintenance()
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = bus, pg_catalog
AS $$
BEGIN
    PERFORM bus.ensure_future_partitions(6);
    PERFORM bus.drop_partition_older_than(3);
END;
$$;
