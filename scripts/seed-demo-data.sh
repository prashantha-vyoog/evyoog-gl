#!/usr/bin/env bash
#
# Seeds demo GL data for a Legal Entity produced by the Setup Wizard:
#   1. Ledger (THICK mode) linked to the Legal Entity
#   2. NATURAL_ACCOUNT Finance Dimension (prerequisite for Chart of Accounts)
#   3. Accounting Calendar (auto-generates FY 2025-26 periods on creation)
#   4. Opens the APR-2025 period for the Legal Entity
#   5. Two Chart of Accounts entries: Cash (ASSET) and Revenue (REVENUE)
#
# Usage:
#   ./scripts/seed-demo-data.sh <legalEntityId>
#
# Env vars:
#   BASE_URL       API base URL (default: http://localhost:8080)
#   ADMIN_EMAIL    login email  (default: admin@evyoog.com)
#   ADMIN_PASSWORD login password (default: Admin@eVyoog1)

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@evyoog.com}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-Admin@eVyoog1}"
LEGAL_ENTITY_ID="${1:-}"

if [[ -z "$LEGAL_ENTITY_ID" ]]; then
    echo "Usage: $0 <legalEntityId>" >&2
    exit 1
fi

for bin in curl jq; do
    command -v "$bin" >/dev/null 2>&1 || { echo "Missing required binary: $bin" >&2; exit 1; }
done

api() {
    local method="$1" path="$2" body="${3:-}"
    local response
    if [[ -n "$body" ]]; then
        response=$(curl -sS -w '\n%{http_code}' -X "$method" "${BASE_URL}${path}" \
            -H "Authorization: Bearer ${ACCESS_TOKEN}" \
            -H "X-User-Id: demo-seed-script" \
            -H "Content-Type: application/json" \
            -d "$body")
    else
        response=$(curl -sS -w '\n%{http_code}' -X "$method" "${BASE_URL}${path}" \
            -H "Authorization: Bearer ${ACCESS_TOKEN}" \
            -H "X-User-Id: demo-seed-script")
    fi

    local http_code payload
    http_code=$(tail -n1 <<<"$response")
    payload=$(sed '$d' <<<"$response")

    if [[ "$http_code" -ge 400 ]]; then
        echo "  FAILED: $method $path -> HTTP $http_code" >&2
        echo "  $payload" >&2
        exit 1
    fi

    echo "$payload"
}

echo "==> Logging in as ${ADMIN_EMAIL}"
LOGIN_RESPONSE=$(curl -sS -X POST "${BASE_URL}/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d "$(jq -n --arg email "$ADMIN_EMAIL" --arg password "$ADMIN_PASSWORD" \
        '{email: $email, password: $password}')")

ACCESS_TOKEN=$(jq -r '.data.accessToken // empty' <<<"$LOGIN_RESPONSE")
if [[ -z "$ACCESS_TOKEN" ]]; then
    echo "Login failed:" >&2
    echo "$LOGIN_RESPONSE" >&2
    exit 1
fi
echo "  Logged in."

echo "==> [1/5] Creating ledger for Legal Entity ${LEGAL_ENTITY_ID}"
LEDGER_RESPONSE=$(api POST /api/v1/gl/ledgers "$(jq -n \
    '{
        code: "PRIM-01",
        name: "Primary Ledger",
        description: "Demo primary ledger",
        financeMode: "THICK",
        ledgerCategory: "PRIMARY",
        functionalCurrency: "INR",
        accountingStandard: "IND_AS"
    }')")
LEDGER_ID=$(jq -r '.data.id' <<<"$LEDGER_RESPONSE")
echo "  Ledger created: ${LEDGER_ID}"

echo "==> [1/5] Linking ledger to Legal Entity"
api POST /api/v1/gl/legal-entity-ledgers "$(jq -n \
    --arg legalEntityId "$LEGAL_ENTITY_ID" \
    --arg ledgerId "$LEDGER_ID" \
    '{legalEntityId: $legalEntityId, ledgerId: $ledgerId, ledgerCategory: "PRIMARY"}')" >/dev/null
echo "  Linked."

echo "==> Creating NATURAL_ACCOUNT Finance Dimension (prerequisite for Chart of Accounts)"
api POST /api/v1/gl/finance-dimensions "$(jq -n \
    --arg ledgerId "$LEDGER_ID" \
    '{
        ledgerId: $ledgerId,
        code: "NAT-ACCT",
        name: "Natural Account",
        description: "Chart of Accounts dimension",
        dimensionType: "NATURAL_ACCOUNT",
        isRequired: true,
        displayOrder: 1
    }')" >/dev/null
echo "  Finance dimension created."

echo "==> [2/5+3/5] Creating accounting calendar (auto-generates FY 2025-26 periods)"
CALENDAR_RESPONSE=$(api POST /api/v1/gl/accounting-calendars "$(jq -n \
    --arg ledgerId "$LEDGER_ID" \
    '{
        ledgerId: $ledgerId,
        name: "FY Calendar",
        description: "Standard Indian fiscal calendar",
        fiscalYearStartMonth: 4,
        fiscalYearStartDay: 1,
        periodType: "MONTHLY",
        initialFiscalYear: 2025
    }')")
CALENDAR_ID=$(jq -r '.data.id' <<<"$CALENDAR_RESPONSE")
GENERATED_COUNT=$(jq -r '.data.generatedPeriodCount' <<<"$CALENDAR_RESPONSE")
echo "  Calendar created: ${CALENDAR_ID} (${GENERATED_COUNT} periods generated for FY 2025-26)"

echo "==> [4/5] Opening APR-2025 period for the Legal Entity"
APR_2025_ID=$(api GET "/api/v1/gl/accounting-periods/find?calendarId=${CALENDAR_ID}&date=2025-04-15" | jq -r '.data.id')
echo "  APR-2025 period id: ${APR_2025_ID}"

PERIOD_STATUS_RESPONSE=$(api POST /api/v1/gl/period-status "$(jq -n \
    --arg legalEntityId "$LEGAL_ENTITY_ID" \
    --arg accountingPeriodId "$APR_2025_ID" \
    '{legalEntityId: $legalEntityId, accountingPeriodId: $accountingPeriodId}')")
PERIOD_STATUS_ID=$(jq -r '.data.id' <<<"$PERIOD_STATUS_RESPONSE")

api POST "/api/v1/gl/period-status/${PERIOD_STATUS_ID}/open" >/dev/null
echo "  APR-2025 period opened."

echo "==> [5/5] Creating Chart of Accounts: Cash (ASSET), Revenue (REVENUE)"
api POST /api/v1/gl/chart-of-accounts "$(jq -n \
    --arg ledgerId "$LEDGER_ID" \
    '{
        ledgerId: $ledgerId,
        code: "1000",
        name: "Cash",
        description: "Cash and bank accounts",
        qualifier: "ASSET",
        isSummary: false,
        isPostable: true,
        normalBalance: "DR",
        displayOrder: 1
    }')" >/dev/null
echo "  Cash (ASSET) account created."

api POST /api/v1/gl/chart-of-accounts "$(jq -n \
    --arg ledgerId "$LEDGER_ID" \
    '{
        ledgerId: $ledgerId,
        code: "4000",
        name: "Revenue",
        description: "Revenue accounts",
        qualifier: "REVENUE",
        isSummary: false,
        isPostable: true,
        normalBalance: "CR",
        displayOrder: 2
    }')" >/dev/null
echo "  Revenue (REVENUE) account created."

echo ""
echo "==> Done. Summary:"
echo "  legalEntityId: ${LEGAL_ENTITY_ID}"
echo "  ledgerId:      ${LEDGER_ID}"
echo "  calendarId:    ${CALENDAR_ID}"
echo "  APR-2025 id:   ${APR_2025_ID} (OPEN)"
