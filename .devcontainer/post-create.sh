#!/bin/bash
# =============================================================================
# eVyoog GL — Codespaces post-create script
# Runs automatically once after the Codespace is created.
# Starts PostgreSQL via Docker, verifies environment, resolves Maven dependencies.
# =============================================================================

set -e

echo ""
echo "╔══════════════════════════════════════════════════════════╗"
echo "║         eVyoog GL — Codespace setup starting             ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""

# ── 1. Verify Java 21 ────────────────────────────────────────────────────────
echo "→ Checking Java version..."
java -version 2>&1 | head -1
echo "✓ Java ready"

# ── 2. Verify Maven ──────────────────────────────────────────────────────────
echo ""
echo "→ Checking Maven..."
mvn -version | head -1
echo "✓ Maven ready"

# ── 3. Verify Docker ─────────────────────────────────────────────────────────
echo ""
echo "→ Checking Docker..."
docker --version
echo "✓ Docker ready"

# ── 4. Start PostgreSQL container ────────────────────────────────────────────
echo ""
echo "→ Starting PostgreSQL 16..."

# Stop and remove any existing container
docker rm -f evyoog-postgres 2>/dev/null || true

# Start fresh PostgreSQL container
docker run -d \
  --name evyoog-postgres \
  --restart unless-stopped \
  -e POSTGRES_DB=evyoog_gl \
  -e POSTGRES_USER=evyoog_app \
  -e POSTGRES_PASSWORD=evyoog_dev_pass \
  -p 5432:5432 \
  postgres:16

echo "→ Waiting for PostgreSQL to be ready..."
MAX_RETRIES=30
RETRY=0
until docker exec evyoog-postgres pg_isready -U evyoog_app -d evyoog_gl > /dev/null 2>&1; do
  RETRY=$((RETRY+1))
  if [ $RETRY -ge $MAX_RETRIES ]; then
    echo "❌ PostgreSQL did not start after $MAX_RETRIES attempts."
    docker logs evyoog-postgres
    exit 1
  fi
  echo "   Waiting... ($RETRY/$MAX_RETRIES)"
  sleep 2
done
echo "✓ PostgreSQL is ready on localhost:5432"

# ── 5. Create schemas and grant permissions ───────────────────────────────────
echo ""
echo "→ Setting up database schemas and permissions..."

docker exec -i evyoog-postgres psql -U evyoog_app -d evyoog_gl << 'SQLEOF'
-- Extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Schemas
CREATE SCHEMA IF NOT EXISTS gl;
CREATE SCHEMA IF NOT EXISTS aie;

-- Grants
GRANT USAGE  ON SCHEMA gl  TO evyoog_app;
GRANT USAGE  ON SCHEMA aie TO evyoog_app;
GRANT CREATE ON SCHEMA gl  TO evyoog_app;
GRANT CREATE ON SCHEMA aie TO evyoog_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA gl  GRANT ALL ON TABLES    TO evyoog_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA gl  GRANT ALL ON SEQUENCES TO evyoog_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA aie GRANT ALL ON TABLES    TO evyoog_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA aie GRANT ALL ON SEQUENCES TO evyoog_app;

SELECT 'Schemas gl and aie ready.' AS status;
SQLEOF

echo "✓ Schemas and permissions configured"

# ── 6. Pre-resolve Maven dependencies ────────────────────────────────────────
if [ -f "pom.xml" ]; then
  echo ""
  echo "→ Resolving Maven dependencies (first run may take 2-3 minutes)..."
  mvn dependency:resolve -q 2>&1 | tail -3
  echo "✓ Maven dependencies resolved"
else
  echo ""
  echo "ℹ  No pom.xml found yet — create the Spring Boot project first (see instructions)"
fi

# ── 7. Print ready summary ───────────────────────────────────────────────────
echo ""
echo "╔══════════════════════════════════════════════════════════╗"
echo "║              eVyoog GL — Environment ready               ║"
echo "╠══════════════════════════════════════════════════════════╣"
echo "║  Java 21 ✓   Maven ✓   Docker ✓   PostgreSQL ✓          ║"
echo "╠══════════════════════════════════════════════════════════╣"
echo "║  Database  : evyoog_gl @ localhost:5432                  ║"
echo "║  User      : evyoog_app                                  ║"
echo "║  Schemas   : gl · aie                                    ║"
echo "╠══════════════════════════════════════════════════════════╣"
echo "║  Commands                                                ║"
echo "║  mvn test              → unit tests (no DB needed)       ║"
echo "║  mvn verify            → all tests (Testcontainers)      ║"
echo "║  mvn spring-boot:run   → start API on port 8080          ║"
echo "║  claude                → open Claude Code                ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""
echo "Ready. Start with GL-01 — paste GL-01_ClaudeCode_Build_Prompt.md into Claude Code."
echo ""
