package com.evyoog.gl.approval.api;

import com.evyoog.gl.posting.domain.JournalSource;
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
class ApprovalControllerIT {

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

    @Test
    void testFullApprovalLifecycle_submit_approve_posted() throws Exception {
        Fixture fx = buildFixture("THICK");
        openPeriod(fx);
        UUID approvalSourceId = createApprovalRequiredSource();

        Map<String, Object> request = balancedRequest(fx);
        request.put("journalSourceId", approvalSourceId.toString());

        UUID journalId = createJournal(request);

        mockMvc.perform(post("/api/v1/gl/journals/{id}/submit", journalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"));

        mockMvc.perform(post("/api/v1/gl/journals/{id}/approve", journalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "performedBy", "approver1",
                                "comments", "Looks good"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.journalHeaderId").value(journalId.toString()))
                .andExpect(jsonPath("$.data.newStatus").value("POSTED"))
                .andExpect(jsonPath("$.data.approvalHistory.length()").value(2));
    }

    @Test
    void testFullRejectionLifecycle_submit_reject_draft() throws Exception {
        Fixture fx = buildFixture("THICK");
        openPeriod(fx);
        UUID approvalSourceId = createApprovalRequiredSource();

        Map<String, Object> request = balancedRequest(fx);
        request.put("journalSourceId", approvalSourceId.toString());

        UUID journalId = createJournal(request);

        mockMvc.perform(post("/api/v1/gl/journals/{id}/submit", journalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"));

        mockMvc.perform(post("/api/v1/gl/journals/{id}/reject", journalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "performedBy", "approver1",
                                "comments", "Wrong account"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newStatus").value("DRAFT"));

        mockMvc.perform(get("/api/v1/gl/journals/{id}", journalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void testRecallLifecycle_submit_recall_draft() throws Exception {
        Fixture fx = buildFixture("THICK");
        openPeriod(fx);
        UUID approvalSourceId = createApprovalRequiredSource();

        Map<String, Object> request = balancedRequest(fx);
        request.put("journalSourceId", approvalSourceId.toString());

        UUID journalId = createJournal(request);

        mockMvc.perform(post("/api/v1/gl/journals/{id}/submit", journalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"));

        mockMvc.perform(post("/api/v1/gl/journals/{id}/recall", journalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "performedBy", "submitter1",
                                "comments", "Need to fix a line"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newStatus").value("DRAFT"));
    }

    @Test
    void testApprovalHistory_recordsAllActions() throws Exception {
        Fixture fx = buildFixture("THICK");
        openPeriod(fx);
        UUID approvalSourceId = createApprovalRequiredSource();

        Map<String, Object> request = balancedRequest(fx);
        request.put("journalSourceId", approvalSourceId.toString());

        UUID journalId = createJournal(request);

        mockMvc.perform(post("/api/v1/gl/journals/{id}/submit", journalId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/gl/journals/{id}/approve", journalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "performedBy", "approver1",
                                "comments", "Approved"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/gl/journals/{id}/approval-history", journalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].fromStatus").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.data[0].toStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data[1].fromStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data[1].toStatus").value("POSTED"));
    }

    @Test
    void testPendingApprovals_returnsCorrectJournals() throws Exception {
        Fixture fx = buildFixture("THICK");
        openPeriod(fx);
        UUID approvalSourceId = createApprovalRequiredSource();

        Map<String, Object> pendingRequest = balancedRequest(fx);
        pendingRequest.put("journalSourceId", approvalSourceId.toString());
        UUID pendingJournalId = createJournal(pendingRequest);
        mockMvc.perform(post("/api/v1/gl/journals/{id}/submit", pendingJournalId))
                .andExpect(status().isOk());

        // A directly-posted journal (no approval source) should NOT show up in the pending queue.
        createJournal(balancedRequest(fx));

        mockMvc.perform(get("/api/v1/gl/approvals/pending").param("legalEntityId", fx.legalEntityId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(pendingJournalId.toString()))
                .andExpect(jsonPath("$.data[0].status").value("PENDING_APPROVAL"));
    }

    private UUID createJournal(Map<String, Object> request) throws Exception {
        String createResponse = mockMvc.perform(post("/api/v1/gl/journals")
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

    // buildFixture() always generates periods for FY2025 (Apr 2025 - Mar 2026) —
    // journal glDate must fall inside that range for date-based period lookup.
    private static final LocalDate JOURNAL_GL_DATE = LocalDate.of(2025, 4, 15);

    private Map<String, Object> balancedRequest(Fixture fx) {
        Map<String, Object> request = new HashMap<>();
        request.put("legalEntityId", fx.legalEntityId.toString());
        request.put("journalSourceId", fx.journalSourceId.toString());
        request.put("journalCategoryId", fx.journalCategoryId.toString());
        request.put("description", "IT journal");
        request.put("glDate", JOURNAL_GL_DATE.toString());
        request.put("currencyCode", "INR");
        request.put("exchangeRate", BigDecimal.ONE);
        request.put("lines", List.of(
                lineOf(fx.cashAccountId, new BigDecimal("100.00"), null),
                lineOf(fx.revenueAccountId, null, new BigDecimal("100.00"))));
        return request;
    }

    private UUID createApprovalRequiredSource() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        JournalSource source = journalSourceRepository.save(JournalSource.builder()
                .code("APPROVAL-" + suffix)
                .name("Approval Required Source")
                .requiresApproval(true)
                .build());
        return source.getId();
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

    private Fixture buildFixture(String financeMode) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID legalEntityId = createThickEsLegalEntity(suffix);
        UUID ledgerId = createLedger("LDG-" + suffix, financeMode);
        assignPrimaryLedger(legalEntityId, ledgerId);
        UUID periodId = createFirstPeriod(ledgerId, suffix);
        UUID naturalAcctDimId = createFinanceDimension(ledgerId, "NA-" + suffix);
        UUID cashAccountId = createDimensionValue(naturalAcctDimId, "1000-" + suffix, "ASSET", false);
        UUID revenueAccountId = createDimensionValue(naturalAcctDimId, "4000-" + suffix, "REVENUE", false);
        UUID journalSourceId = journalSourceRepository.findByCode("MANUAL").orElseThrow().getId();
        UUID journalCategoryId = journalCategoryRepository.findByCode("ADJUSTMENT").orElseThrow().getId();

        return new Fixture(legalEntityId, ledgerId, periodId, naturalAcctDimId,
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

    private UUID createFirstPeriod(UUID ledgerId, String suffix) throws Exception {
        Map<String, Object> calendarRequest = new HashMap<>();
        calendarRequest.put("ledgerId", ledgerId.toString());
        calendarRequest.put("name", "FY Calendar " + suffix);
        calendarRequest.put("initialFiscalYear", 2025);

        String response = mockMvc.perform(post("/api/v1/gl/accounting-calendars")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(calendarRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID calendarId = UUID.fromString(objectMapper.readTree(response).at("/data/id").asText());

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
        final UUID periodId;
        final UUID naturalAcctDimId;
        final UUID cashAccountId;
        final UUID revenueAccountId;
        final UUID journalSourceId;
        final UUID journalCategoryId;

        Fixture(UUID legalEntityId, UUID ledgerId, UUID periodId, UUID naturalAcctDimId,
                UUID cashAccountId, UUID revenueAccountId, UUID journalSourceId, UUID journalCategoryId) {
            this.legalEntityId = legalEntityId;
            this.ledgerId = ledgerId;
            this.periodId = periodId;
            this.naturalAcctDimId = naturalAcctDimId;
            this.cashAccountId = cashAccountId;
            this.revenueAccountId = revenueAccountId;
            this.journalSourceId = journalSourceId;
            this.journalCategoryId = journalCategoryId;
        }
    }
}
