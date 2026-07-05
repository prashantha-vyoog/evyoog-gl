# CLAUDE.md — eVyoog ERP · GL Module

> This file is read automatically by Claude Code at the start of every session.
> It is the authoritative reference for all implementation decisions.
> Do not contradict it. Do not deviate from it without explicit instruction from the Domain SME.

---

## Who you are building for

**Product**: eVyoog ERP — a capability-based modular ERP for Indian discrete manufacturing SMEs.
**Domain SME**: Prashanth — provides business rules and validates correctness.
**Your role**: Implementer. You write the code. The Domain SME decides if it is correct.

---

## Architecture — PLATFORM_FOUNDATION_v2.0

### Pattern: Capability-based Modular (NOT microservices)

- All GL capabilities share **one Spring Boot application** and **one PostgreSQL database**
- Schema boundary is at the **module level** — `gl` schema owns all GL tables, `aie` schema owns AIE tables
- **Write isolation**: the GL service writes ONLY to `gl.*` and `aie.*` — no other schema, ever
- **Cross-schema reads**: permitted for reporting (SQL JOINs) — never for writes
- **Transactional cross-module flows**: events only — never direct cross-schema writes
- **Capability**: the activation and billing unit — independently activatable per customer via `gl.capability_registry`

### The four rules you must never break

```
Rule 1 — Write isolation:  GL service writes ONLY to gl.* and aie.*
Rule 2 — Audit every write: every INSERT/UPDATE on any entity → audit_log in the SAME transaction
Rule 3 — Soft delete only: never DELETE from gl.*. Set is_active = FALSE
Rule 4 — UUID keys: all primary keys are UUID via uuid_generate_v4()
```

---

## Tech stack

| Component | Choice |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.x |
| ORM | Spring Data JPA + Hibernate |
| Database | PostgreSQL 16 |
| Migrations | Flyway (classpath:db/migration) |
| DTO mapping | MapStruct 1.5.5 |
| Boilerplate | Lombok |
| API docs | Springdoc OpenAPI 3 (Swagger UI at /swagger-ui.html) |
| JSONB mapping | Hibernate 7 native (Spring Boot 4) — no external library needed |
| Unit tests | JUnit 5 + Mockito |
| Integration tests | Testcontainers (PostgreSQL container) |
| Build | Maven |

---

## Database

```
Database name : evyoog_gl
Schemas       : gl (25 tables)  ·  aie (6 tables)
Total         : 31 tables · 92 indexes · 3 views
Extensions    : uuid-ossp · pgcrypto
Migrations    : Flyway V1__baseline.sql = full evyoog_gl_schema_v2.sql
                Subsequent: V2__, V3__ etc — one per capability
```

### Connection (from environment variables — never hardcode)

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/evyoog_gl}
    username: ${SPRING_DATASOURCE_USERNAME:evyoog_app}
    password: ${SPRING_DATASOURCE_PASSWORD:evyoog_dev_pass}
  jpa:
    hibernate:
      ddl-auto: validate   # Flyway manages schema — JPA only validates
    properties:
      hibernate:
        default_schema: gl
```

### JSONB columns

`gl.journal_line.account_combination` and `gl.account_balance.account_combination` are `jsonb`. Map with:

```java
// Hibernate 7 JSONB mapping for Map<String,String> and Map<String,Object>:
// Use @JdbcTypeCode(SqlTypes.JSON) on the field
// Import: org.hibernate.annotations.JdbcTypeCode and org.hibernate.type.SqlTypes
@Column(name = "account_combination", columnDefinition = "jsonb")
private Map<String, String> accountCombination;
```

---

## Project package structure

```
com.evyoog.gl
├── EvyoogGlApplication.java
├── config/                        ← DataSource, OpenAPI, Flyway config
├── common/
│   ├── audit/                     ← AuditLog entity + AuditService (shared by ALL capabilities)
│   ├── exception/                 ← EvyoogException, ResourceNotFoundException, GlobalExceptionHandler
│   └── response/                  ← ApiResponse<T> wrapper
├── enterprise/                    ← GL-01 Enterprise Setup
│   ├── api/                       ← Controllers
│   ├── service/                   ← Business logic + validation
│   ├── repository/                ← Spring Data JPA repositories
│   ├── domain/                    ← JPA entities
│   ├── dto/                       ← Request + Response DTOs
│   └── mapper/                    ← MapStruct mappers
├── ledger/                        ← GL-03 Ledger Management
├── dimension/                     ← GL-04 Finance Dimensions
├── coa/                           ← GL-05 Chart of Accounts
├── calendar/                      ← GL-08 Accounting Calendar
├── period/                        ← GL-09 + GL-10 Period Management + Status
├── journal/                       ← GL-11 through GL-15 Journal + Posting Engine
├── aie/                           ← GL-16 through GL-20 AIE Pipeline
├── balance/                       ← GL-21 Account Balance
├── reporting/                     ← GL-22 through GL-26 Reports
└── localisation/                  ← GL-27 + GL-28 India GST / TDS
```

---

## Non-negotiable implementation rules

### 1. Audit trail — every write, same transaction

```java
@Service
@RequiredArgsConstructor
public class BusinessGroupService {

    private final BusinessGroupRepository repository;
    private final AuditService auditService;

    @Transactional
    public BusinessGroupResponse create(CreateBusinessGroupRequest request) {
        BusinessGroup entity = mapper.toEntity(request);
        BusinessGroup saved = repository.save(entity);

        // Audit MUST be in same transaction
        auditService.log(AuditAction.CREATE, "business_group", saved.getId(),
                        null, saved, requestContext.getUserId());

        return mapper.toResponse(saved);
    }
}
```

### 2. Never expose JPA entities in API responses

```java
// WRONG — never do this
@GetMapping("/{id}")
public BusinessGroup getById(@PathVariable UUID id) { ... }

// RIGHT — always use DTOs
@GetMapping("/{id}")
public ApiResponse<BusinessGroupResponse> getById(@PathVariable UUID id) { ... }
```

### 3. Structured error responses — always

```java
// Every error response must follow this shape:
{
  "status": 409,
  "code": "THIN_ES_LE_LIMIT",
  "message": "Thin ES Business Groups support exactly one Legal Entity.",
  "field": "businessGroupId",
  "timestamp": "2026-06-01T10:30:00Z"
}
```

### 4. Soft delete — never hard delete

```java
// WRONG
repository.deleteById(id);

// RIGHT
entity.setIsActive(false);
repository.save(entity);
auditService.log(AuditAction.DELETE, ...);
```

### 5. finance_mode_snapshot — set once, never update

```java
// On journal_header creation ONLY:
journalHeader.setFinanceModeSnapshot(ledger.getFinanceMode());
// Never update this field after creation, ever.
```

---

## Business rules by area

### Thin ES constraints

```java
// A Business Group with es_mode = THIN_ES may have exactly ONE Legal Entity
if (businessGroup.getEsMode() == EsMode.THIN_ES) {
    long count = legalEntityRepository.countByBusinessGroupId(businessGroup.getId());
    if (count >= 1) {
        throw new EvyoogException("THIN_ES_LE_LIMIT",
            "Thin ES Business Groups support exactly one Legal Entity.");
    }
}

// A Ledger with finance_mode = THIN may have exactly TWO Finance Dimensions
if (ledger.getFinanceMode() == FinanceMode.THIN) {
    long count = financeDimensionRepository.countByLedgerId(ledger.getId());
    if (count >= 2) {
        throw new EvyoogException("THIN_DIMENSION_LIMIT",
            "Thin mode Ledgers support exactly two Finance Dimensions: LEGAL_ENTITY and NATURAL_ACCOUNT.");
    }
}
```

### GSTIN validation

```java
private static final Pattern GSTIN_PATTERN =
    Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$");

public void validateGstin(String gstin) {
    if (gstin != null && !GSTIN_PATTERN.matcher(gstin).matches()) {
        throw new ValidationException("INVALID_GSTIN",
            "GSTIN must be a valid 15-character Indian GST number.");
    }
}
```

### Posting engine — mode branching (read finance_mode ONCE)

```java
@Service
public class PostingEngine {

    public PostingResult post(JournalHeader header, List<JournalLine> lines) {
        // Read finance_mode ONCE — never check it again inside this method
        FinanceMode mode = header.getLedger().getFinanceMode();

        return switch (mode) {
            case THICK  -> postThick(header, lines);
            case THIN   -> postThin(header, lines);
            case EVENT_ONLY -> emitEvent(header, lines);
        };
    }

    private PostingResult postThick(JournalHeader header, List<JournalLine> lines) {
        // All 8 validation rules
        validateBalance(lines);
        validatePeriodOpen(header);
        validateAccounts(lines, header.getLedger());
        validatePostableAccounts(lines);
        validateDimensionValues(lines, header.getLedger());
        validateLegalEntityAuthorisation(header);
        validateCurrency(header);
        validateApproval(header);
        // Update account_balance atomically
        updateAccountBalance(header, lines);
        return PostingResult.posted(header.getId());
    }
}
```

### Period gate — check on every posting

```java
PeriodStatus status = periodStatusRepository
    .findByLegalEntityIdAndAccountingPeriodId(
        header.getLegalEntityId(), header.getAccountingPeriodId())
    .orElseThrow(() -> new EvyoogException("PERIOD_NOT_FOUND", "Period not open for this Legal Entity."));

if (status.getStatus() != PeriodStatusEnum.OPEN) {
    throw new EvyoogException("PERIOD_NOT_OPEN",
        "Accounting period is " + status.getStatus() + ". Cannot post journals to a non-open period.");
}
```

---

## Three financial modes — what each allows

| Behaviour | THICK | THIN | EVENT_ONLY |
|---|---|---|---|
| Journal entries | Yes — full double-entry | Yes — lightweight | No |
| Finance Dimensions | 2–15 | Exactly 2 | None required |
| Period status gate | Yes | Yes | No (Phase 1) |
| account_balance update | Yes | Yes | No |
| Period-close workflow | Full 7-step | Simplified | None |
| Trial Balance | Full | Simplified | N/A |
| Balance Sheet | Yes | No | N/A |
| Reversal journals | Yes | No | N/A |
| Recurring journals | Yes | No | N/A |
| SLA events to Kafka | No | No | Yes |

---

## India localisation

### GST model — 1 BU = 1 State = 1 GSTIN

```
Business Unit A (Tamil Nadu) → GSTIN: 33AABCE1234F1Z5
Business Unit B (Maharashtra) → GSTIN: 27AABCE1234F1Z5
Both BUs belong to the same Legal Entity.
```

- Intra-state transaction: CGST + SGST (each at half the rate)
- Inter-state transaction: IGST (full rate)
- GST flags on `gl.journal_line`: `gst_applicable`, `gst_type` (CGST/SGST/IGST/UTGST)

### TDS

- Fields on `gl.journal_line`: `tds_applicable`, `tds_section` (194C, 194J, 194H, 192, etc.)
- `gl.legal_entity.tan` — Tax Deduction Account Number

### Fiscal year

- April 1 to March 31 — default for all new calendars
- Period naming: APR-2025, MAY-2025, ..., MAR-2026
- Fiscal year name: 2025-26

---

## Testing requirements

### Unit test pattern

```java
@ExtendWith(MockitoExtension.class)
class LegalEntityServiceTest {

    @Mock
    private LegalEntityRepository repository;
    @Mock
    private BusinessGroupRepository bgRepository;
    @Mock
    private AuditService auditService;
    @InjectMocks
    private LegalEntityService service;

    @Test
    void createLegalEntity_whenThinEsAlreadyHasOne_shouldThrow409() {
        // given
        BusinessGroup bg = BusinessGroup.builder().esMode(EsMode.THIN_ES).build();
        when(bgRepository.findById(any())).thenReturn(Optional.of(bg));
        when(repository.countByBusinessGroupId(any())).thenReturn(1L);

        // when / then
        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "THIN_ES_LE_LIMIT");
    }
}
```

### Integration test pattern

```java
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class LegalEntityControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("evyoog_gl_test")
            .withUsername("evyoog_app")
            .withPassword("test_pass");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
}
```

### Commands to run

```bash
mvn test             # Unit tests — no DB needed (~30 seconds)
mvn verify           # Unit + integration tests — Testcontainers spins up PostgreSQL
mvn spring-boot:run  # Start app on port 8080 — test API via Codespaces port-forwarding
```

---

## API response envelope

All API responses use a consistent envelope:

```java
public record ApiResponse<T>(
    boolean success,
    T data,
    String message,
    List<FieldError> errors
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }
    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(true, data, null, null);
    }
    public static ApiResponse<?> error(String message, List<FieldError> errors) {
        return new ApiResponse<>(false, null, message, errors);
    }
}
```

```
HTTP 200 OK          → successful GET
HTTP 201 Created     → successful POST
HTTP 400 Bad Request → validation failure (field-level errors)
HTTP 404 Not Found   → resource does not exist
HTTP 409 Conflict    → business rule violation (Thin ES limit, duplicate GSTIN, etc.)
HTTP 422 Unprocessable → posting validation failure (period not open, DR ≠ CR, etc.)
```

---

## Build sequence — current position

Always check which capability is being built in this session. Follow this sequence strictly:

```
✓ = done and merged
→ = next to build
  = not started

✓ GL-01 Enterprise Setup
✓ GL-02 Setup Wizard
✓ GL-03 Ledger Management
✓ GL-04 Finance Dimension Management
→ GL-05 Chart of Accounts Management
  GL-08 Accounting Calendar
  GL-09 Period Management
  GL-10 Period Status Control
  GL-15 Posting Engine
  GL-11 Manual Journal Entry
  GL-21 Account Balance Maintenance
  GL-22 Trial Balance            ← First major milestone 🎯
```

---

## Before ending any session

Run this checklist before considering a capability done:

```bash
# 1. All tests pass
mvn verify

# 2. App starts cleanly
mvn spring-boot:run

# 3. OpenAPI spec generated
curl http://localhost:8080/api-docs

# 4. Commit and push
git add .
git commit -m "feat(GL-XX): <capability name> — all tests passing"
git push origin main
```

---

## What NOT to do

- Do NOT use `spring.jpa.hibernate.ddl-auto: create` or `update` — Flyway manages the schema
- Do NOT hard-delete rows from `gl.*` — soft delete only (`is_active = FALSE`)
- Do NOT expose JPA entities in API responses — always use DTOs
- Do NOT scatter `finance_mode` checks across multiple services — the Posting Engine reads it once and branches
- Do NOT update `journal_header.finance_mode_snapshot` after creation — it is immutable
- Do NOT accept free-text dimension values — validate against `dimension_value` master before use
- Do NOT write to any schema other than `gl.*` and `aie.*`
- Do NOT create a second Legal Entity under a THIN_ES Business Group
- Do NOT create a third Finance Dimension under a THIN mode Ledger
- Do NOT post a journal to a period that is not OPEN for the journal's Legal Entity

---

*eVyoog ERP · CLAUDE.md · PLATFORM_FOUNDATION_v2.0 · GL_CORE_v1.0*
*Keep this file in the root of the repository. Claude Code reads it automatically.*

---

## Technical Deviations — Discovered During Build (GL-02 through GL-13)

These correct the original CLAUDE.md assumptions. Claude Code MUST read these
before building any future capability.

### GL-02 — ConsumptionContext field name
- Field is `segmentType` (NOT `contextType`) on ConsumptionContext entity
- ConsumptionContext has: `code`, `name`, `segmentType`, `organisationName`,
  `primaryContactName`, `primaryContactEmail`

### GL-02 — provisioningAnswers
- `provisioningAnswers` column was missing from the V1 baseline entity
- Added via V2 migration — now present on ConsumptionContext

### GL-04 — DimensionValue parentValue
- `DimensionValue.parentValue` is a `@ManyToOne` entity relation (NOT a raw UUID field)
- Access via `dv.getParentValue().getId()` — not `dv.getParentValueId()`

### GL-05 — DimensionType enum
- `DimensionType` enum acts as Oracle-style segment qualifier on `DimensionValue`
- Used as `segmentType` on `ConsumptionContext` (confirmed naming)

### GL-05 — Intercompany dimension values
- IC dimension values require `counterparty_legal_entity_id` FK (added via V5 migration)

### GL-05 — Cost Centre metadata
- `dimension_value` has: `cc_manager_name`, `cc_manager_email`, `cc_department`,
  `valid_from`, `valid_to`, `budget_controlled` (added via V5 migration)

### GL-11 — JSONB on Map fields (Hibernate 7)
- Hibernate 7 native JSONB requires `@JdbcTypeCode(SqlTypes.JSON)` on Map fields
- Do NOT use `hibernate-types-60` or `@Type` annotation — these do not work with Hibernate 7
```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "account_combination", columnDefinition = "jsonb")
private Map<String, String> accountCombination;
```

### GL-11 — currency codes
- Use `VARCHAR(3)` NOT `CHAR(3)` for currency code columns

### GL-11 — AuditableEntity
- Subclasses MUST NOT redeclare `isActive` — it is already on `AuditableEntity`
- Redeclaring it causes Hibernate mapping conflicts

### GL-11 — Timestamp annotations
- Use `@CreationTimestamp` / `@UpdateTimestamp` with `Instant` type
- Do NOT use manual `OffsetDateTime.now()` defaults

### GL-11 — JournalLine debit/credit columns
- `JournalLine` uses SPLIT columns: `debitAmount` and `creditAmount` (BigDecimal)
- NOT a single `amount` + `debitCredit` string as the design doc assumed
- Reversal (GL-13) flips by swapping the two fields

### GL-12 — PostingEngine row creation (CRITICAL)
- `PostingEngine.post()` always creates a NEW JournalHeader row (new ID, new journal number)
- It does NOT update existing rows in place — this is tested, load-bearing behavior
- Do NOT attempt to refactor PostingEngine in any future capability
- ApprovalService.approve() follows this pattern:
    original row updated to APPROVED → PostingEngine creates new POSTED row
    approval-history and approve endpoint keyed to original journal ID

### GL-12 — Period-open gate location
- The period-open gate lives SOLELY inside `PostingEngine.postThick()`
- Do NOT duplicate this check in ApprovalService, ReversalService, or any other service

### GL-13 — Reversal column names (CRITICAL)
- Reversal columns are: `reversal_of_id` (NOT `original_journal_id`) and `is_reversal`
- Both exist from V9 baseline — V19 only added a partial index
- `findByOriginalJournalId` query method must use `reversal_of_id` column

### GL-13 — Lombok Boolean wrapper
- `is_reversal` maps to `Boolean` (wrapper, NOT boolean primitive)
- Lombok generates: `getIsReversal()` / `setIsReversal(Boolean)`
- NOT `setReversal()` — the "is" stripping only applies to primitive boolean

### GL-13 — JournalHeader entity relations
- `legalEntity`, `ledger`, `accountingPeriod` on JournalHeader are `@ManyToOne` entity relations
- NOT raw UUID fields — access via `journal.getLegalEntity().getId()` etc.

### GL-13 — REVERSAL seed data
- `REVERSAL` journal_source and journal_category already seeded from V9 baseline
- Do NOT re-insert in any future migration

### General — .claude/ folder
- `.claude/` is in `.gitignore` — do not commit Claude Code session files

### General — Spring Boot version
- Actual version: Spring Boot 4.0.7 (NOT 3.3.x as original CLAUDE.md stated)
- Java 21 (Microsoft build), Hibernate 7, MapStruct 1.6.3, Springdoc OpenAPI 3.0.2


### GL-14 — recurring_journal_template
- Table did NOT exist in V1 baseline — created in full in V20
- Template lines stored as typed POJO list (RecurringTemplateLine), NOT List<Map<String,Object>>
- Raw Map<String,Object> deserializes BigDecimal as Double via Hibernate 7 JSON — 
  always use a typed POJO for JSONB that contains numeric fields

### GL-14 — Lombok boolean vs Boolean convention
- primitive boolean fields (isActive on AuditableEntity): Lombok generates
  isActive() / setActive(false)  ← no "Is" in setter
- Boolean wrapper fields (isReversal on JournalHeader): Lombok generates
  getIsReversal() / setIsReversal(Boolean)  ← "Is" preserved in both
- Check FinanceDimensionService.deactivate() as the reference pattern for isActive
- Check JournalHeader as the reference pattern for Boolean wrapper fields

### GL-14 — journal_category RECURRING seed
- RECURRING journal_source: already seeded in V9
- RECURRING journal_category: NOT seeded until V20 — seeded there
