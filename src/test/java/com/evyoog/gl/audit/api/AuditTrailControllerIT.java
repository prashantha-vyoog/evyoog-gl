package com.evyoog.gl.audit.api;

import com.evyoog.gl.posting.repository.JournalCategoryRepository;
import com.evyoog.gl.posting.repository.JournalSourceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AuditTrailControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("evyoog_gl_test")
            .withUsername("evyoog_app")
            .withPassword("evyoog_test_pass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JournalSourceRepository journalSourceRepository;
    @Autowired private JournalCategoryRepository journalCategoryRepository;

    // buildFixture() always generates periods for FY2025 (Apr 2025 - Mar 2026) —
    // journal glDate must fall inside that range for date-based period lookup.
    private static final LocalDate JOURNAL_GL_DATE = LocalDate.of(2025, 4, 15);

    @Test
    void testGetAuditByEntity_journalHeader_returnsHistory() throws Exception {
        Fixture fx = buildFixture();
        openPeriod(fx);
        UUID journalId = createJournal(balancedRequest(fx), "auditor1");

        mockMvc.perform(get("/api/v1/gl/audit/entities/{entityType}/{entityId}", "journal_header", journalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].entityId").value(journalId.toString()))
                .andExpect(jsonPath("$.data.content[0].action").value("CREATE"))
                .andExpect(jsonPath("$.data.content[0].performedBy").value("auditor1"));
    }

    @Test
    void testGetAuditByUser_returnsActions() throws Exception {
        Fixture fx = buildFixture();
        openPeriod(fx);
        createJournal(balancedRequest(fx), "user-alpha");

        mockMvc.perform(get("/api/v1/gl/audit/users/{performedBy}", "user-alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isNotEmpty())
                .andExpect(jsonPath("$.data.content[0].performedBy").value("user-alpha"));
    }

    @Test
    void testGetAuditByDateRange_returnsCorrectEntries() throws Exception {
        Fixture fx = buildFixture();
        openPeriod(fx);
        UUID journalId = createJournal(balancedRequest(fx), "auditor2");

        java.time.Instant from = java.time.Instant.now().minusSeconds(3600);
        java.time.Instant to = java.time.Instant.now().plusSeconds(3600);

        mockMvc.perform(get("/api/v1/gl/audit")
                        .param("entityType", "journal_header")
                        .param("entityId", journalId.toString())
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].entityId").value(journalId.toString()));
    }

    @Test
    void testGetJournalAuditHistory_fullLifecycle() throws Exception {
        Fixture fx = buildFixture();
        openPeriod(fx);
        UUID journalId = createJournal(balancedRequest(fx), "auditor3");

        mockMvc.perform(post("/api/v1/gl/journals/{id}/reverse", journalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "targetPeriodId", fx.periodId.toString(),
                                "reversedBy", "reverser1"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/gl/audit/journals/{journalId}", journalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    void testGetAudit_noFilters_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/gl/audit"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUDIT_FILTER_REQUIRED"));
    }

    @Test
    void testPagination_defaultPageSize() throws Exception {
        Fixture fx = buildFixture();
        openPeriod(fx);
        UUID journalId = createJournal(balancedRequest(fx), "auditor4");

        mockMvc.perform(get("/api/v1/gl/audit/entities/{entityType}/{entityId}", "journal_header", journalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20));
    }

    private UUID createJournal(Map<String, Object> request, String userId) throws Exception {
        String createResponse = mockMvc.perform(post("/api/v1/gl/journals")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(createResponse).at("/data/id").asText());
    }

    private Map<String, Object> lineOf(UUID naturalAccountValueId, BigDecimal debit, BigDecimal credit) {
        Map<String, Object> line = new HashMap<>();
        line.put("accountCombination", Map.of());
        line.put("naturalAccountValueId", naturalAccountValueId.toString());
        if (debit != null) {
            line.put("debitAmount", debit);
        }
        if (credit != null) {
            line.put("creditAmount", credit);
        }
        return line;
    }

    private Map<String, Object> balancedRequest(Fixture fx) {
        Map<String, Object> request = new HashMap<>();
        request.put("legalEntityId", fx.legalEntityId.toString());
        request.put("journalSourceId", fx.journalSourceId.toString());
        request.put("journalCategoryId", fx.journalCategoryId.toString());
        request.put("description", "Audit trail IT journal");
        request.put("glDate", JOURNAL_GL_DATE.toString());
        request.put("currencyCode", "INR");
        request.put("exchangeRate", BigDecimal.ONE);
        request.put("lines", List.of(
                lineOf(fx.cashAccountId, new BigDecimal("100.00"), null),
                lineOf(fx.revenueAccountId, null, new BigDecimal("100.00"))));
        return request;
    }

    private void openPeriod(Fixture fx) throws Exception {
        String createResponse = mockMvc.perform(post("/api/v1/gl/period-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "legalEntityId", fx.legalEntityId.toString(),
                                "accountingPeriodId", fx.periodId.toString()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID statusId = UUID.fromString(objectMapper.readTree(createResponse).at("/data/id").asText());

        mockMvc.perform(post("/api/v1/gl/period-status/{id}/open", statusId))
                .andExpect(status().isOk());
    }

    private Fixture buildFixture() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID legalEntityId = createThickEsLegalEntity(suffix);
        UUID ledgerId = createLedger("LDG-" + suffix, "THICK");
        assignPrimaryLedger(legalEntityId, ledgerId);
        UUID calendarId = createCalendar(ledgerId, suffix);
        UUID periodId = firstPeriodId(calendarId);
        UUID naturalAcctDimId = createFinanceDimension(ledgerId, "NA-" + suffix);
        UUID cashAccountId = createDimensionValue(naturalAcctDimId, "1000-" + suffix, "ASSET", false);
        UUID revenueAccountId = createDimensionValue(naturalAcctDimId, "4000-" + suffix, "REVENUE", false);
        UUID journalSourceId = journalSourceRepository.findByCode("MANUAL").orElseThrow().getId();
        UUID journalCategoryId = journalCategoryRepository.findByCode("ADJUSTMENT").orElseThrow().getId();

        return new Fixture(legalEntityId, ledgerId, calendarId, periodId,
                cashAccountId, revenueAccountId, journalSourceId, journalCategoryId);
    }

    private UUID createFinanceDimension(UUID ledgerId, String code) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("ledgerId", ledgerId.toString());
        request.put("code", code);
        request.put("name", "Natural Account " + code);
        request.put("dimensionType", "NATURAL_ACCOUNT");
        request.put("isRequired", true);

        String response = mockMvc.perform(post("/api/v1/gl/finance-dimensions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).at("/data/id").asText());
    }

    private UUID createDimensionValue(UUID financeDimensionId, String code, String accountQualifier, boolean isSummary) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("financeDimensionId", financeDimensionId.toString());
        request.put("code", code);
        request.put("name", "Account " + code);
        request.put("accountQualifier", accountQualifier);
        request.put("isSummary", isSummary);
        request.put("isPostable", !isSummary);

        String response = mockMvc.perform(post("/api/v1/gl/dimension-values")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).at("/data/id").asText());
    }

    private UUID createCalendar(UUID ledgerId, String suffix) throws Exception {
        Map<String, Object> calendarRequest = new HashMap<>();
        calendarRequest.put("ledgerId", ledgerId.toString());
        calendarRequest.put("name", "FY Calendar " + suffix);
        calendarRequest.put("initialFiscalYear", 2025);

        String response = mockMvc.perform(post("/api/v1/gl/accounting-calendars")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(calendarRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).at("/data/id").asText());
    }

    private UUID firstPeriodId(UUID calendarId) throws Exception {
        String periodsResponse = mockMvc.perform(get("/api/v1/gl/accounting-calendars/{calendarId}/periods", calendarId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(periodsResponse).at("/data/0/id").asText());
    }

    private void assignPrimaryLedger(UUID legalEntityId, UUID ledgerId) throws Exception {
        mockMvc.perform(post("/api/v1/gl/legal-entity-ledgers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "legalEntityId", legalEntityId.toString(),
                                "ledgerId", ledgerId.toString(),
                                "ledgerCategory", "PRIMARY"))))
                .andExpect(status().isCreated());
    }

    private UUID createLedger(String code, String financeMode) throws Exception {
        Map<String, Object> request = Map.of(
                "code", code,
                "name", "Ledger " + code,
                "financeMode", financeMode);

        String response = mockMvc.perform(post("/api/v1/gl/ledgers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).at("/data/id").asText());
    }

    private UUID createThickEsLegalEntity(String suffix) throws Exception {
        UUID contextId = createConsumptionContext("CTX-" + suffix);
        UUID businessGroupId = createBusinessGroup(contextId, "BG-" + suffix, "THICK_ES");

        Map<String, Object> request = Map.of(
                "businessGroupId", businessGroupId.toString(),
                "code", "LE-" + suffix,
                "name", "Legal Entity " + suffix);

        String response = mockMvc.perform(post("/api/v1/gl/legal-entities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).at("/data/id").asText());
    }

    private UUID createConsumptionContext(String code) throws Exception {
        Map<String, Object> request = Map.of(
                "segmentType", "WORKSPACE",
                "code", code,
                "name", "Coimbatore Manufacturing Group");

        String response = mockMvc.perform(post("/api/v1/gl/consumption-contexts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).at("/data/id").asText());
    }

    private UUID createBusinessGroup(UUID contextId, String code, String esMode) throws Exception {
        Map<String, Object> request = Map.of(
                "consumptionContextId", contextId.toString(),
                "code", code,
                "name", "Coimbatore Manufacturing Group",
                "esMode", esMode,
                "defaultCurrency", "INR");

        String response = mockMvc.perform(post("/api/v1/gl/business-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).at("/data/id").asText());
    }

    private static final class Fixture {
        final UUID legalEntityId;
        final UUID ledgerId;
        final UUID calendarId;
        final UUID periodId;
        final UUID cashAccountId;
        final UUID revenueAccountId;
        final UUID journalSourceId;
        final UUID journalCategoryId;

        Fixture(UUID legalEntityId, UUID ledgerId, UUID calendarId, UUID periodId,
                UUID cashAccountId, UUID revenueAccountId, UUID journalSourceId, UUID journalCategoryId) {
            this.legalEntityId = legalEntityId;
            this.ledgerId = ledgerId;
            this.calendarId = calendarId;
            this.periodId = periodId;
            this.cashAccountId = cashAccountId;
            this.revenueAccountId = revenueAccountId;
            this.journalSourceId = journalSourceId;
            this.journalCategoryId = journalCategoryId;
        }
    }
}
