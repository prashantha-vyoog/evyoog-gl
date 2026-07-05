package com.evyoog.gl.aie.api;

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
class AieControllerIT {

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

    @Test
    void testFullPipeline_validBatch_journalPosted() throws Exception {
        Fixture fx = buildFixture();
        openPeriod(fx);

        Map<String, Object> request = importRequest(fx, "EVT-" + UUID.randomUUID());

        mockMvc.perform(post("/api/v1/aie/journals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("POSTED"))
                .andExpect(jsonPath("$.data.journalNumber").isNotEmpty())
                .andExpect(jsonPath("$.data.validLines").value(2))
                .andExpect(jsonPath("$.data.errorLines").value(0));
    }

    @Test
    void testDuplicateEventId_returns409() throws Exception {
        Fixture fx = buildFixture();
        openPeriod(fx);
        String eventId = "EVT-" + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/aie/journals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(importRequest(fx, eventId))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/aie/journals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(importRequest(fx, eventId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EVENT_ID"));
    }

    @Test
    void testUnbalancedBatch_returns422WithErrors() throws Exception {
        Fixture fx = buildFixture();
        openPeriod(fx);

        Map<String, Object> request = importRequest(fx, "EVT-" + UUID.randomUUID());
        List<Object> lines = List.of(
                lineOf(1, fx.cashAccountCode, new java.math.BigDecimal("500.00"), null),
                lineOf(2, fx.revenueAccountCode, null, new java.math.BigDecimal("400.00")));
        request.put("lines", lines);

        mockMvc.perform(post("/api/v1/aie/journals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.errors[0].errorCode").value("UNBALANCED"));
    }

    @Test
    void testGetBatchStatus_afterPost_returnsPosted() throws Exception {
        Fixture fx = buildFixture();
        openPeriod(fx);

        String response = mockMvc.perform(post("/api/v1/aie/journals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(importRequest(fx, "EVT-" + UUID.randomUUID()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID batchId = UUID.fromString(objectMapper.readTree(response).at("/data/batchId").asText());

        mockMvc.perform(get("/api/v1/aie/batches/{batchId}", batchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("POSTED"))
                .andExpect(jsonPath("$.data.journalHeaderId").isNotEmpty());
    }

    @Test
    void testGetErrors_afterValidationFailure_returnsErrors() throws Exception {
        Fixture fx = buildFixture();
        openPeriod(fx);

        Map<String, Object> request = importRequest(fx, "EVT-" + UUID.randomUUID());
        List<Object> lines = List.of(
                lineOf(1, fx.cashAccountCode, new java.math.BigDecimal("500.00"), null),
                lineOf(2, fx.revenueAccountCode, null, new java.math.BigDecimal("400.00")));
        request.put("lines", lines);

        String response = mockMvc.perform(post("/api/v1/aie/journals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andReturn().getResponse().getContentAsString();
        UUID batchId = UUID.fromString(objectMapper.readTree(response).at("/data/batchId").asText());

        mockMvc.perform(get("/api/v1/aie/batches/{batchId}/errors", batchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].errorCode").value("UNBALANCED"))
                .andExpect(jsonPath("$.data[0].errorStage").value("VALIDATE"));
    }

    @Test
    void testResubmit_failedBatch_postsSuccessfully() throws Exception {
        Fixture fx = buildFixture();
        openPeriod(fx);

        Map<String, Object> request = importRequest(fx, "EVT-" + UUID.randomUUID());
        List<Object> lines = List.of(
                lineOf(1, fx.cashAccountCode, new java.math.BigDecimal("500.00"), null),
                lineOf(2, fx.revenueAccountCode, null, new java.math.BigDecimal("400.00")));
        request.put("lines", lines);

        String failedResponse = mockMvc.perform(post("/api/v1/aie/journals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andReturn().getResponse().getContentAsString();
        UUID batchId = UUID.fromString(objectMapper.readTree(failedResponse).at("/data/batchId").asText());

        mockMvc.perform(post("/api/v1/aie/batches/{batchId}/resubmit", batchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("resubmittedBy", "resubmitter1"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.eventId").value(org.hamcrest.Matchers.startsWith("RESUBMIT-")));
    }

    @Test
    void testResubmit_postedBatch_returns409() throws Exception {
        Fixture fx = buildFixture();
        openPeriod(fx);

        String response = mockMvc.perform(post("/api/v1/aie/journals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(importRequest(fx, "EVT-" + UUID.randomUUID()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID batchId = UUID.fromString(objectMapper.readTree(response).at("/data/batchId").asText());

        mockMvc.perform(post("/api/v1/aie/batches/{batchId}/resubmit", batchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("resubmittedBy", "resubmitter1"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BATCH_NOT_FAILED"));
    }

    private Map<String, Object> importRequest(Fixture fx, String eventId) {
        Map<String, Object> request = new HashMap<>();
        request.put("eventId", eventId);
        request.put("sourceSystem", "SAP");
        request.put("legalEntityId", fx.legalEntityId.toString());
        request.put("ledgerId", fx.ledgerId.toString());
        request.put("accountingPeriodId", fx.periodId.toString());
        request.put("batchReference", "BATCH-1");
        request.put("description", "AIE import");
        request.put("createdBy", "aie-user");
        request.put("lines", List.of(
                lineOf(1, fx.cashAccountCode, new java.math.BigDecimal("500.00"), null),
                lineOf(2, fx.revenueAccountCode, null, new java.math.BigDecimal("500.00"))));
        return request;
    }

    private Map<String, Object> lineOf(int lineNumber, String accountCode, java.math.BigDecimal debit, java.math.BigDecimal credit) {
        Map<String, Object> line = new HashMap<>();
        line.put("lineNumber", lineNumber);
        line.put("accountCode", accountCode);
        line.put("accountCombination", Map.of());
        if (debit != null) {
            line.put("debitAmount", debit);
        }
        if (credit != null) {
            line.put("creditAmount", credit);
        }
        return line;
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
        String cashAccountCode = "1000-" + suffix;
        String revenueAccountCode = "4000-" + suffix;
        createDimensionValue(naturalAcctDimId, cashAccountCode, "ASSET", false);
        createDimensionValue(naturalAcctDimId, revenueAccountCode, "REVENUE", false);

        return new Fixture(legalEntityId, ledgerId, periodId, cashAccountCode, revenueAccountCode);
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
        final UUID periodId;
        final String cashAccountCode;
        final String revenueAccountCode;

        Fixture(UUID legalEntityId, UUID ledgerId, UUID periodId, String cashAccountCode, String revenueAccountCode) {
            this.legalEntityId = legalEntityId;
            this.ledgerId = ledgerId;
            this.periodId = periodId;
            this.cashAccountCode = cashAccountCode;
            this.revenueAccountCode = revenueAccountCode;
        }
    }
}
