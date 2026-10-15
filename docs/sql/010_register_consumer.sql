CREATE OR REPLACE FUNCTION register_consumer(
    p_consumer_name TEXT
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path FROM CURRENT
AS $$
BEGIN
    INSERT INTO consumer_registry(consumer_name)
    VALUES (p_consumer_name)
    ON CONFLICT (consumer_name) DO NOTHING;
END;
$$;
