package com.evyoog.gl.aie.sourceref.api;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class SourceReferenceControllerIT {

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

    private static final LocalDate JOURNAL_GL_DATE = LocalDate.of(2025, 4, 15);

    @Test
    void testCreateAndRetrieve_fullLifecycle() throws Exception {
        Fixture fx = buildFixture();
        openPeriod(fx);
        UUID journalId = createPostedJournal(fx);

        Map<String, Object> request = Map.of(
                "journalHeaderId", journalId.toString(),
                "sourceSystem", "AP",
                "sourceDocumentType", "INVOICE",
                "sourceDocumentId", "INV-3001",
                "sourceDocumentRef", "AP/INV-3001",
                "amount", "1000.00",
                "createdBy", "it-test");

        String createResponse = mockMvc.perform(post("/api/v1/aie/source-references")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.journalHeaderId").value(journalId.toString()))
                .andExpect(jsonPath("$.data.sourceDocumentId").value("INV-3001"))
                .andReturn().getResponse().getContentAsString();

        UUID refId = UUID.fromString(objectMapper.readTree(createResponse).at("/data/id").asText());

        mockMvc.perform(get("/api/v1/aie/source-references/{id}", refId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(refId.toString()))
                .andExpect(jsonPath("$.data.journalNumber").exists());
    }

    @Test
    void testCreate_journalNotFound_returns404() throws Exception {
        Map<String, Object> request = Map.of(
                "journalHeaderId", UUID.randomUUID().toString(),
                "sourceSystem", "AP",
                "sourceDocumentType", "INVOICE",
                "sourceDocumentId", "INV-3002");

        mockMvc.perform(post("/api/v1/aie/source-references")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOURNAL_NOT_FOUND"));
    }

    @Test
    void testGetByJournal_returnsCorrectReferences() throws Exception {
        Fixture fx = buildFixture();
        openPeriod(fx);
        UUID journalId = createPostedJournal(fx);

        createSourceReference(journalId, "AP", "INVOICE", "INV-3003");
        createSourceReference(journalId, "PO", "PO", "PO-9001");

        mockMvc.perform(get("/api/v1/aie/source-references/journal/{journalHeaderId}", journalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void testGetBySource_returnsLinkedJournals() throws Exception {
        Fixture fx = buildFixture();
        openPeriod(fx);
        UUID journalId = createPostedJournal(fx);
        createSourceReference(journalId, "AP", "INVOICE", "INV-3004");

        mockMvc.perform(get("/api/v1/aie/source-references/source/{sourceSystem}/{sourceDocumentId}", "AP", "INV-3004"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].journalHeaderId").value(journalId.toString()));
    }

    @Test
    void testDelete_draftJournal_returns204() throws Exception {
        Fixture fx = buildFixture();
        openPeriod(fx);
        UUID journalId = createDraftJournal(fx);
        UUID refId = createSourceReference(journalId, "AP", "INVOICE", "INV-3005");

        mockMvc.perform(delete("/api/v1/aie/source-references/{id}", refId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/aie/source-references/{id}", refId))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDelete_postedJournal_returns409() throws Exception {
        Fixture fx = buildFixture();
        openPeriod(fx);
        UUID journalId = createPostedJournal(fx);
        UUID refId = createSourceReference(journalId, "AP", "INVOICE", "INV-3006");

        mockMvc.perform(delete("/api/v1/aie/source-references/{id}", refId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_DELETE_POSTED_REF"));
    }

    @Test
    void testGetById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/aie/source-references/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SOURCE_REF_NOT_FOUND"));
    }

    private UUID createSourceReference(UUID journalId, String sourceSystem, String docType, String docId) throws Exception {
        Map<String, Object> request = Map.of(
                "journalHeaderId", journalId.toString(),
                "sourceSystem", sourceSystem,
                "sourceDocumentType", docType,
                "sourceDocumentId", docId,
                "createdBy", "it-test");

        String response = mockMvc.perform(post("/api/v1/aie/source-references")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).at("/data/id").asText());
    }

    private UUID createPostedJournal(Fixture fx) throws Exception {
        String response = mockMvc.perform(post("/api/v1/gl/journals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(balancedRequest(fx, fx.journalSourceId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("POSTED"))
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).at("/data/id").asText());
    }

    private UUID createDraftJournal(Fixture fx) throws Exception {
        UUID approvalSourceId = createApprovalRequiredSource();

        String response = mockMvc.perform(post("/api/v1/gl/journals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(balancedRequest(fx, approvalSourceId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).at("/data/id").asText());
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

    private Map<String, Object> balancedRequest(Fixture fx, UUID journalSourceId) {
        Map<String, Object> request = new HashMap<>();
        request.put("legalEntityId", fx.legalEntityId.toString());
        request.put("journalSourceId", journalSourceId.toString());
        request.put("journalCategoryId", fx.journalCategoryId.toString());
        request.put("description", "GL-19 IT journal");
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
        UUID periodId = createFirstPeriod(ledgerId, suffix);
        UUID naturalAcctDimId = createFinanceDimension(ledgerId, "NA-" + suffix);
        UUID cashAccountId = createDimensionValue(naturalAcctDimId, "1000-" + suffix, "ASSET", false);
        UUID revenueAccountId = createDimensionValue(naturalAcctDimId, "4000-" + suffix, "REVENUE", false);
        UUID journalSourceId = journalSourceRepository.findByCode("MANUAL").orElseThrow().getId();
        UUID journalCategoryId = journalCategoryRepository.findByCode("ADJUSTMENT").orElseThrow().getId();

        return new Fixture(legalEntityId, ledgerId, periodId, cashAccountId, revenueAccountId,
                journalSourceId, journalCategoryId);
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
        final UUID cashAccountId;
        final UUID revenueAccountId;
        final UUID journalSourceId;
        final UUID journalCategoryId;

        Fixture(UUID legalEntityId, UUID ledgerId, UUID periodId, UUID cashAccountId, UUID revenueAccountId,
                UUID journalSourceId, UUID journalCategoryId) {
            this.legalEntityId = legalEntityId;
            this.ledgerId = ledgerId;
            this.periodId = periodId;
            this.cashAccountId = cashAccountId;
            this.revenueAccountId = revenueAccountId;
            this.journalSourceId = journalSourceId;
            this.journalCategoryId = journalCategoryId;
        }
    }
}
