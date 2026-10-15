-- Grant access in the schema selected by bootstrap.sql / init.sql.
-- Replace app_role with the existing application role before manual execution.
DO $$
DECLARE
    v_schema TEXT := current_schema();
BEGIN
    EXECUTE format('REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA %I FROM app_role', v_schema);
    EXECUTE format('REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA %I FROM app_role', v_schema);
    EXECUTE format('GRANT USAGE ON SCHEMA %I TO app_role', v_schema);
    EXECUTE format('GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA %I TO app_role', v_schema);
    EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA %I GRANT EXECUTE ON FUNCTIONS TO app_role', v_schema);
END;
$$;
