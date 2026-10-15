-- Grant access in the schema selected by 001_bootstrap.sql / init.sql.
-- Manual installation: set v_role below to the existing application role.
DO $$
DECLARE
    v_schema TEXT := current_schema();
    v_role TEXT := 'app_role';
BEGIN
    EXECUTE format('REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA %I FROM %I', v_schema, v_role);
    EXECUTE format('REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA %I FROM %I', v_schema, v_role);
    EXECUTE format('GRANT USAGE ON SCHEMA %I TO %I', v_schema, v_role);
    EXECUTE format('GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA %I TO %I', v_schema, v_role);
    EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA %I GRANT EXECUTE ON FUNCTIONS TO %I', v_schema, v_role);
END;
$$;
