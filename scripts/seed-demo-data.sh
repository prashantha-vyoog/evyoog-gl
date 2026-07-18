#!/usr/bin/env bash
#
# Seeds a realistic demo dataset for "Orbinox Valves India Pvt Ltd" under a
# Legal Entity produced by the Setup Wizard:
#   1. Ledger (THICK mode) linked to the Legal Entity
#   2. NATURAL_ACCOUNT Finance Dimension (prerequisite for Chart of Accounts)
#   3. Accounting Calendar (auto-generates FY 2025-26 periods on creation)
#   4. Opens the APR-2025 period for the Legal Entity
#   5. 25 Chart of Accounts entries (Assets/Liabilities/Equity/Revenue/Expense)
#   6. 10 posted journal entries covering opening balances, sales, purchases,
#      payroll and depreciation for April 2025
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

echo "==> [1/8] Checking for existing ledger on Legal Entity ${LEGAL_ENTITY_ID}"
EXISTING_LEDGER_ID=$(api GET "/api/v1/gl/legal-entity-ledgers?legalEntityId=${LEGAL_ENTITY_ID}" \
    | jq -r '.data[] | select(.ledgerCode == "PRIM-01") | .ledgerId' | head -n1)

if [[ -n "$EXISTING_LEDGER_ID" ]]; then
    LEDGER_ID="$EXISTING_LEDGER_ID"
    echo "  Ledger already linked: ${LEDGER_ID}"
else
    LEDGER_RESPONSE=$(api POST /api/v1/gl/ledgers "$(jq -n \
        '{
            code: "PRIM-01",
            name: "Primary Ledger",
            description: "Orbinox Valves India Pvt Ltd primary ledger",
            financeMode: "THICK",
            ledgerCategory: "PRIMARY",
            functionalCurrency: "INR",
            accountingStandard: "IND_AS"
        }')")
    LEDGER_ID=$(jq -r '.data.id' <<<"$LEDGER_RESPONSE")
    echo "  Ledger created: ${LEDGER_ID}"

    echo "==> [1/8] Linking ledger to Legal Entity"
    api POST /api/v1/gl/legal-entity-ledgers "$(jq -n \
        --arg legalEntityId "$LEGAL_ENTITY_ID" \
        --arg ledgerId "$LEDGER_ID" \
        '{legalEntityId: $legalEntityId, ledgerId: $ledgerId, ledgerCategory: "PRIMARY"}')" >/dev/null
    echo "  Linked."
fi

echo "==> [2/8] Checking for existing NATURAL_ACCOUNT Finance Dimension"
EXISTING_DIMENSION=$(api GET "/api/v1/gl/finance-dimensions?ledgerId=${LEDGER_ID}&dimensionType=NATURAL_ACCOUNT" \
    | jq -r '.data | length')

if [[ "$EXISTING_DIMENSION" -gt 0 ]]; then
    echo "  NATURAL_ACCOUNT Finance Dimension already exists — skipping."
else
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
fi

echo "==> [3/8] Checking for existing accounting calendar"
EXISTING_CALENDAR_ID=$(api GET "/api/v1/gl/accounting-calendars?ledgerId=${LEDGER_ID}" | jq -r '.data.id // empty')

if [[ -n "$EXISTING_CALENDAR_ID" ]]; then
    CALENDAR_ID="$EXISTING_CALENDAR_ID"
    echo "  Calendar already exists: ${CALENDAR_ID}"
else
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
fi

echo "==> [4/8] Checking for existing APR-2025 period status"
APR_2025_ID=$(api GET "/api/v1/gl/accounting-periods/find?calendarId=${CALENDAR_ID}&date=2025-04-15" | jq -r '.data.id')
echo "  APR-2025 period id: ${APR_2025_ID}"

EXISTING_PERIOD_STATUS_ID=$(api GET "/api/v1/gl/period-status?legalEntityId=${LEGAL_ENTITY_ID}" \
    | jq -r '.data[] | select(.periodName == "APR-2025") | .id' | head -n1)

if [[ -n "$EXISTING_PERIOD_STATUS_ID" ]]; then
    PERIOD_STATUS_ID="$EXISTING_PERIOD_STATUS_ID"
    echo "  APR-2025 period status already exists: ${PERIOD_STATUS_ID} — skipping create/open."
else
    PERIOD_STATUS_RESPONSE=$(api POST /api/v1/gl/period-status "$(jq -n \
        --arg legalEntityId "$LEGAL_ENTITY_ID" \
        --arg accountingPeriodId "$APR_2025_ID" \
        '{legalEntityId: $legalEntityId, accountingPeriodId: $accountingPeriodId}')")
    PERIOD_STATUS_ID=$(jq -r '.data.id' <<<"$PERIOD_STATUS_RESPONSE")

    api POST "/api/v1/gl/period-status/${PERIOD_STATUS_ID}/open" >/dev/null
    echo "  APR-2025 period opened."
fi

echo "==> [5/8] Fetching MANUAL journal source and ACCRUAL journal category ids"
MANUAL_SOURCE_ID=$(api GET /api/v1/gl/journal-sources | jq -r '.data[] | select(.code=="MANUAL") | .id')
ACCRUAL_CATEGORY_ID=$(api GET /api/v1/gl/journal-categories | jq -r '.data[] | select(.code=="ACCRUAL") | .id')
echo "  journalSourceId (MANUAL):   ${MANUAL_SOURCE_ID}"
echo "  journalCategoryId (ACCRUAL): ${ACCRUAL_CATEGORY_ID}"

echo "==> [6/8] Creating Chart of Accounts (25 accounts)"

# code|name|qualifier
ACCOUNTS=(
    "1100|Cash and Cash Equivalents|ASSET"
    "1200|Bank - HDFC Current Account|ASSET"
    "1300|Accounts Receivable|ASSET"
    "1400|Raw Material Inventory|ASSET"
    "1500|Work in Progress|ASSET"
    "1600|Finished Goods Inventory|ASSET"
    "1700|Plant and Machinery|ASSET"
    "1800|Accumulated Depreciation|ASSET"
    "2100|Accounts Payable|LIABILITY"
    "2200|GST Payable|LIABILITY"
    "2300|TDS Payable|LIABILITY"
    "2400|Employee Salaries Payable|LIABILITY"
    "2500|Short Term Bank Loan|LIABILITY"
    "3100|Share Capital|EQUITY"
    "3200|Retained Earnings|EQUITY"
    "4100|Valve Sales - Domestic|REVENUE"
    "4200|Valve Sales - Export|REVENUE"
    "4300|Service and AMC Revenue|REVENUE"
    "5100|Raw Material Consumed|EXPENSE"
    "5200|Direct Labour Cost|EXPENSE"
    "5300|Factory Overhead|EXPENSE"
    "5400|Salaries and Staff Cost|EXPENSE"
    "5500|Depreciation|EXPENSE"
    "5600|Marketing and Sales Expense|EXPENSE"
    "5700|Administrative Expenses|EXPENSE"
)

declare -A ACCOUNT_ID

for entry in "${ACCOUNTS[@]}"; do
    IFS='|' read -r code name qualifier <<<"$entry"

    existing_id=$(api GET "/api/v1/gl/chart-of-accounts/search?ledgerId=${LEDGER_ID}&query=${code}" \
        | jq -r --arg code "$code" '.data[] | select(.code == $code) | .id' | head -n1)

    if [[ -n "$existing_id" ]]; then
        ACCOUNT_ID["$code"]="$existing_id"
        echo "  ${code} ${name} already exists -> ${existing_id}"
        continue
    fi

    ACCOUNT_RESPONSE=$(api POST /api/v1/gl/chart-of-accounts "$(jq -n \
        --arg ledgerId "$LEDGER_ID" \
        --arg code "$code" \
        --arg name "$name" \
        --arg qualifier "$qualifier" \
        '{
            ledgerId: $ledgerId,
            code: $code,
            name: $name,
            qualifier: $qualifier,
            isSummary: false,
            isPostable: true
        }')")
    acct_id=$(jq -r '.data.id' <<<"$ACCOUNT_RESPONSE")
    ACCOUNT_ID["$code"]="$acct_id"
    echo "  ${code} ${name} (${qualifier}) -> ${acct_id}"
done

echo "==> [7/8] Posting journal entries"

EXISTING_POSTED=$(api GET "/api/v1/gl/journals?legalEntityId=${LEGAL_ENTITY_ID}&status=POSTED&fromDate=2025-04-01&toDate=2025-04-30&size=50" \
    | jq -r '.data.content | length')

# description|glDate|drCode|drAmount|crCode|crAmount
JOURNALS=(
    "Opening Balances|2025-04-01|1200|20000000|3100|20000000"
    "Domestic Valve Sales|2025-04-05|1300|7500000|4100|7500000"
    "Export Valve Sales - Bank Receipt|2025-04-08|1200|2000000|4200|2000000"
    "Service Revenue|2025-04-10|1300|500000|4300|500000"
    "Raw Material Purchase|2025-04-12|1400|2500000|2100|2500000"
    "Raw Material Consumed|2025-04-15|5100|2200000|1400|2200000"
    "Direct Labour Cost|2025-04-20|5200|1000000|2400|1000000"
    "Factory Overhead|2025-04-22|5300|800000|2100|800000"
    "Staff Salaries|2025-04-25|5400|300000|2400|300000"
    "Depreciation|2025-04-30|5500|200000|1800|200000"
)

JOURNAL_NUMBERS=()

if [[ "$EXISTING_POSTED" -ge "${#JOURNALS[@]}" ]]; then
    echo "  ${EXISTING_POSTED} journals already posted for APR-2025 — skipping journal creation."
else
    for entry in "${JOURNALS[@]}"; do
        IFS='|' read -r description glDate drCode drAmount crCode crAmount <<<"$entry"

        dr_id="${ACCOUNT_ID[$drCode]}"
        cr_id="${ACCOUNT_ID[$crCode]}"

        LINES_JSON=$(jq -n \
            --arg drId "$dr_id" --arg drCode "$drCode" --argjson drAmount "$drAmount" \
            --arg crId "$cr_id" --arg crCode "$crCode" --argjson crAmount "$crAmount" \
            '[
                {naturalAccountValueId: $drId, accountCombination: {NATURAL_ACCOUNT: $drCode}, debitAmount: $drAmount},
                {naturalAccountValueId: $crId, accountCombination: {NATURAL_ACCOUNT: $crCode}, creditAmount: $crAmount}
            ]')

        JOURNAL_BODY=$(jq -n \
            --arg legalEntityId "$LEGAL_ENTITY_ID" \
            --arg journalSourceId "$MANUAL_SOURCE_ID" \
            --arg journalCategoryId "$ACCRUAL_CATEGORY_ID" \
            --arg glDate "$glDate" \
            --arg description "$description" \
            --argjson lines "$LINES_JSON" \
            '{
                legalEntityId: $legalEntityId,
                journalSourceId: $journalSourceId,
                journalCategoryId: $journalCategoryId,
                glDate: $glDate,
                description: $description,
                lines: $lines
            }')

        JOURNAL_RESPONSE=$(api POST /api/v1/gl/journals "$JOURNAL_BODY")
        journal_number=$(jq -r '.data.journalNumber' <<<"$JOURNAL_RESPONSE")
        journal_status=$(jq -r '.data.status' <<<"$JOURNAL_RESPONSE")
        JOURNAL_NUMBERS+=("$journal_number")
        echo "  Posted: ${description} -> ${journal_number} (${journal_status})"
    done
fi

echo ""
echo "==> [8/8] Done. Summary:"
echo "  legalEntityId: ${LEGAL_ENTITY_ID}"
echo "  ledgerId:      ${LEDGER_ID}"
echo "  calendarId:    ${CALENDAR_ID}"
echo "  APR-2025 id:   ${APR_2025_ID} (OPEN)"
echo ""
echo "  Chart of Accounts (${#ACCOUNTS[@]}):"
for entry in "${ACCOUNTS[@]}"; do
    IFS='|' read -r code name qualifier <<<"$entry"
    echo "    ${code}  ${name}  (${qualifier})  -> ${ACCOUNT_ID[$code]}"
done
echo ""
if [[ "${#JOURNAL_NUMBERS[@]}" -gt 0 ]]; then
    echo "  Journals posted (${#JOURNAL_NUMBERS[@]}):"
    for jn in "${JOURNAL_NUMBERS[@]}"; do
        echo "    ${jn}"
    done
else
    echo "  Journals: none posted this run (already present)."
fi
echo ""
echo "  Expected April 2025 P&L for Orbinox Valves India Pvt Ltd:"
echo "    Revenue:  INR 1,00,00,000  (Domestic 75,00,000 + Export 20,00,000 + Service 5,00,000)"
echo "    Expenses: INR 45,00,000    (Raw Material 22,00,000 + Labour 10,00,000 + Overhead 8,00,000 + Salaries 3,00,000 + Depreciation 2,00,000)"
echo "    Profit:   INR 55,00,000"
