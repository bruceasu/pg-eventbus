CREATE OR REPLACE FUNCTION bus.register_consumer(
    p_consumer_name TEXT
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = bus, pg_catalog
AS $$
BEGIN
    INSERT INTO bus.consumer_registry(consumer_name)
    VALUES (p_consumer_name)
    ON CONFLICT (consumer_name) DO NOTHING;
END;
$$;
