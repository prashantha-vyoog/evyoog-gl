-- =============================================================================
-- eVyoog GL — PostgreSQL initialisation
-- Run once automatically when the postgres container starts for the first time.
-- Creates the required schemas and grants permissions to evyoog_app.
-- Flyway (via Spring Boot) creates all tables, indexes, and views on app startup.
-- =============================================================================

-- Extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Schemas
CREATE SCHEMA IF NOT EXISTS gl;
CREATE SCHEMA IF NOT EXISTS aie;

-- Grant schema-level permissions to the app user
-- GL service may write to gl.* and aie.* only — no other schemas
GRANT USAGE  ON SCHEMA gl  TO evyoog_app;
GRANT USAGE  ON SCHEMA aie TO evyoog_app;
GRANT CREATE ON SCHEMA gl  TO evyoog_app;
GRANT CREATE ON SCHEMA aie TO evyoog_app;
GRANT ALL PRIVILEGES ON ALL TABLES    IN SCHEMA gl  TO evyoog_app;
GRANT ALL PRIVILEGES ON ALL TABLES    IN SCHEMA aie TO evyoog_app;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA gl  TO evyoog_app;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA aie TO evyoog_app;

-- Default privileges — applies to future tables created by Flyway
ALTER DEFAULT PRIVILEGES IN SCHEMA gl
    GRANT ALL ON TABLES    TO evyoog_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA gl
    GRANT ALL ON SEQUENCES TO evyoog_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA aie
    GRANT ALL ON TABLES    TO evyoog_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA aie
    GRANT ALL ON SEQUENCES TO evyoog_app;

-- Confirm setup
SELECT 'eVyoog GL database initialised successfully.' AS status;
