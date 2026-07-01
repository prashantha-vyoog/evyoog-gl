#!/bin/bash
# =============================================================================
# eVyoog GL — Codespaces post-create script
# Runs automatically once after the Codespace is created.
# Verifies the environment, resolves Maven dependencies, and prints a summary.
# =============================================================================

set -e

echo ""
echo "╔══════════════════════════════════════════════════════════╗"
echo "║         eVyoog GL — Codespace setup starting             ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""

# ── 1. Verify Java 21 ────────────────────────────────────────────────────────
echo "→ Checking Java version..."
java -version
JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VER" -lt 21 ]; then
  echo "❌ Java 21 is required. Found: $JAVA_VER"
  exit 1
fi
echo "✓ Java $JAVA_VER detected"

# ── 2. Verify Maven ──────────────────────────────────────────────────────────
echo ""
echo "→ Checking Maven..."
mvn -version
echo "✓ Maven ready"

# ── 3. Verify Docker ─────────────────────────────────────────────────────────
echo ""
echo "→ Checking Docker (required for Testcontainers)..."
docker --version
echo "✓ Docker ready"

# ── 4. Wait for PostgreSQL ───────────────────────────────────────────────────
echo ""
echo "→ Waiting for PostgreSQL to be ready..."
MAX_RETRIES=30
RETRY=0
until pg_isready -h postgres -U evyoog_app -d evyoog_gl > /dev/null 2>&1; do
  RETRY=$((RETRY+1))
  if [ $RETRY -ge $MAX_RETRIES ]; then
    echo "❌ PostgreSQL did not become ready after $MAX_RETRIES attempts."
    echo "   Check docker-compose logs: docker logs <postgres-container>"
    exit 1
  fi
  echo "   Waiting... ($RETRY/$MAX_RETRIES)"
  sleep 2
done
echo "✓ PostgreSQL is ready at postgres:5432"

# ── 5. Verify database and schemas ───────────────────────────────────────────
echo ""
echo "→ Verifying database schemas..."
SCHEMAS=$(psql -h postgres -U evyoog_app -d evyoog_gl -t \
  -c "SELECT schema_name FROM information_schema.schemata WHERE schema_name IN ('gl','aie') ORDER BY schema_name;")
echo "   Schemas found: $SCHEMAS"
echo "✓ Database schemas verified"

# ── 6. Pre-resolve Maven dependencies ────────────────────────────────────────
if [ -f "pom.xml" ]; then
  echo ""
  echo "→ Resolving Maven dependencies (this may take 2-3 minutes on first run)..."
  mvn dependency:resolve -q 2>&1 | tail -3
  echo "✓ Maven dependencies resolved"
else
  echo ""
  echo "ℹ  No pom.xml found yet — run Spring Initializr or create pom.xml to get started"
fi

# ── 7. Print environment summary ─────────────────────────────────────────────
echo ""
echo "╔══════════════════════════════════════════════════════════╗"
echo "║              eVyoog GL — Environment ready               ║"
echo "╠══════════════════════════════════════════════════════════╣"
echo "║  Java 21     ✓   Maven ✓   Docker ✓   PostgreSQL ✓      ║"
echo "╠══════════════════════════════════════════════════════════╣"
echo "║  Database  : evyoog_gl (postgres:5432)                   ║"
echo "║  User      : evyoog_app                                  ║"
echo "║  Schemas   : gl · aie                                    ║"
echo "╠══════════════════════════════════════════════════════════╣"
echo "║  Commands                                                ║"
echo "║  mvn test              → run unit tests                  ║"
echo "║  mvn verify            → run all tests (Testcontainers)  ║"
echo "║  mvn spring-boot:run   → start API on port 8080          ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""
echo "Ready to build eVyoog GL — start with GL-01 Enterprise Setup."
echo ""
