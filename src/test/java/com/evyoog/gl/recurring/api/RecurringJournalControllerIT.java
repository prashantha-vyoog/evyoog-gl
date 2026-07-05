package com.evyoog.gl.recurring.api;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class RecurringJournalControllerIT {

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
    void testCreateTemplate_andGenerate_journalPosted() throws Exception {
        Fixture fx = buildFixture("THICK");
        openPeriod(fx);

        UUID templateId = createTemplate(fx);

        mockMvc.perform(post("/api/v1/gl/recurring-templates/{id}/generate", templateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "targetPeriodId", fx.periodId.toString(),
                                "generatedBy", "generator1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.templateId").value(templateId.toString()))
                .andExpect(jsonPath("$.data.journalStatus").value("POSTED"))
                .andExpect(jsonPath("$.data.journalNumber").isNotEmpty());
    }

    @Test
    void testGenerate_duplicatePeriod_returns409() throws Exception {
        Fixture fx = buildFixture("THICK");
        openPeriod(fx);

        UUID templateId = createTemplate(fx);
        Map<String, Object> generateBody = Map.of(
                "targetPeriodId", fx.periodId.toString(),
                "generatedBy", "generator1");

        mockMvc.perform(post("/api/v1/gl/recurring-templates/{id}/generate", templateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(generateBody)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/gl/recurring-templates/{id}/generate", templateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(generateBody)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_GENERATED"));
    }

    @Test
    void testGenerate_inactiveTemplate_returns409() throws Exception {
        Fixture fx = buildFixture("THICK");
        openPeriod(fx);

        UUID templateId = createTemplate(fx);

        mockMvc.perform(patch("/api/v1/gl/recurring-templates/{id}/deactivate", templateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("deactivatedBy", "deactivator1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(false));

        mockMvc.perform(post("/api/v1/gl/recurring-templates/{id}/generate", templateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "targetPeriodId", fx.periodId.toString(),
                                "generatedBy", "generator1"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEMPLATE_INACTIVE"));
    }

    @Test
    void testDeactivateTemplate_thenGenerate_returns409() throws Exception {
        Fixture fx = buildFixture("THICK");
        openPeriod(fx);

        UUID templateId = createTemplate(fx);

        mockMvc.perform(patch("/api/v1/gl/recurring-templates/{id}/deactivate", templateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("deactivatedBy", "deactivator1"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/gl/recurring-templates", templateId)
                        .param("legalEntityId", fx.legalEntityId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(post("/api/v1/gl/recurring-templates/{id}/generate", templateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "targetPeriodId", fx.periodId.toString(),
                                "generatedBy", "generator1"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEMPLATE_INACTIVE"));
    }

    @Test
    void testGetRuns_returnsGenerationHistory() throws Exception {
        Fixture fx = buildFixture("THICK");
        openPeriod(fx);

        UUID templateId = createTemplate(fx);

        mockMvc.perform(post("/api/v1/gl/recurring-templates/{id}/generate", templateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "targetPeriodId", fx.periodId.toString(),
                                "generatedBy", "generator1"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/gl/recurring-templates/{id}/runs", templateId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].templateId").value(templateId.toString()))
                .andExpect(jsonPath("$.data[0].generatedBy").value("generator1"));
    }

    private UUID createTemplate(Fixture fx) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("legalEntityId", fx.legalEntityId.toString());
        request.put("ledgerId", fx.ledgerId.toString());
        request.put("name", "Monthly Rent");
        request.put("description", "Rent accrual");
        request.put("frequency", "MONTHLY");
        request.put("dayOfMonth", 1);
        request.put("lines", List.of(
                lineOf(fx.cashAccountId, new BigDecimal("500.00"), null),
                lineOf(fx.revenueAccountId, null, new BigDecimal("500.00"))));
        request.put("createdBy", "creator1");

        String response = mockMvc.perform(post("/api/v1/gl/recurring-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).at("/data/id").asText());
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
        UUID calendarId = createCalendar(ledgerId, suffix);
        UUID periodId = firstPeriodId(calendarId);
        UUID naturalAcctDimId = createFinanceDimension(ledgerId, "NA-" + suffix);
        UUID cashAccountId = createDimensionValue(naturalAcctDimId, "1000-" + suffix, "ASSET", false);
        UUID revenueAccountId = createDimensionValue(naturalAcctDimId, "4000-" + suffix, "REVENUE", false);

        return new Fixture(legalEntityId, ledgerId, calendarId, periodId, cashAccountId, revenueAccountId);
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

        Fixture(UUID legalEntityId, UUID ledgerId, UUID calendarId, UUID periodId,
                UUID cashAccountId, UUID revenueAccountId) {
            this.legalEntityId = legalEntityId;
            this.ledgerId = ledgerId;
            this.calendarId = calendarId;
            this.periodId = periodId;
            this.cashAccountId = cashAccountId;
            this.revenueAccountId = revenueAccountId;
        }
    }
}
