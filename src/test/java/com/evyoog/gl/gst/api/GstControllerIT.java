package com.evyoog.gl.gst.api;

import com.evyoog.gl.posting.dto.PostingLineRequest;
import com.evyoog.gl.posting.dto.PostingRequest;
import com.evyoog.gl.posting.repository.JournalCategoryRepository;
import com.evyoog.gl.posting.repository.JournalSourceRepository;
import com.evyoog.gl.posting.service.PostingEngine;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class GstControllerIT {

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
    @Autowired private PostingEngine postingEngine;
    @Autowired private JournalSourceRepository journalSourceRepository;
    @Autowired private JournalCategoryRepository journalCategoryRepository;

    @Test
    void testGstr3b_afterPostingGstJournals_correctTotals() throws Exception {
        Fixture fx = buildFixture();
        openPeriod(fx);

        // Sale of 1000 within Tamil Nadu: CGST 90 + SGST 90 collected (liability lines)
        postingEngine.post(request(fx, LocalDate.of(2025, 4, 5),
                line(fx.cashAccountId, "1180.00", null, false, null),
                line(fx.salesAccountId, null, "1000.00", false, null),
                line(fx.cgstPayableId, null, "90.00", true, "CGST"),
                line(fx.sgstPayableId, null, "90.00", true, "SGST")));

        String response = mockMvc.perform(get("/api/v1/gl/gst/gstr3b")
                        .param("legalEntityId", fx.legalEntityId.toString())
                        .param("periodId", fx.periodId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var data = objectMapper.readTree(response).at("/data");

        assertThat(data.get("totalCgstCollected").decimalValue()).isEqualByComparingTo("90.00");
        assertThat(data.get("totalSgstCollected").decimalValue()).isEqualByComparingTo("90.00");
        assertThat(data.get("totalIgstCollected").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(data.get("netTaxPayable").decimalValue()).isEqualByComparingTo("180.00");
        assertThat(data.get("gstin").asText()).isEqualTo(fx.gstin);
    }

    @Test
    void testGstr1_outwardSuppliesFiltered() throws Exception {
        Fixture fx = buildFixture();
        openPeriod(fx);

        postingEngine.post(request(fx, LocalDate.of(2025, 4, 6),
                line(fx.cashAccountId, "1180.00", null, false, null),
                line(fx.salesAccountId, null, "1000.00", false, null),
                line(fx.cgstPayableId, null, "90.00", true, "CGST"),
                line(fx.sgstPayableId, null, "90.00", true, "SGST")));

        String response = mockMvc.perform(get("/api/v1/gl/gst/gstr1")
                        .param("legalEntityId", fx.legalEntityId.toString())
                        .param("periodId", fx.periodId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var data = objectMapper.readTree(response).at("/data");

        assertThat(data.get("outwardSupplies")).hasSize(2);
        assertThat(data.get("transactionCount").asInt()).isEqualTo(2);
        assertThat(data.get("totalTax").decimalValue()).isEqualByComparingTo("180.00");
    }

    @Test
    void testGstExport_createsJobAndReturns201() throws Exception {
        Fixture fx = buildFixture();
        openPeriod(fx);

        postingEngine.post(request(fx, LocalDate.of(2025, 4, 7),
                line(fx.cashAccountId, "1180.00", null, false, null),
                line(fx.salesAccountId, null, "1000.00", false, null),
                line(fx.cgstPayableId, null, "90.00", true, "CGST"),
                line(fx.sgstPayableId, null, "90.00", true, "SGST")));

        Map<String, Object> exportRequest = Map.of(
                "legalEntityId", fx.legalEntityId.toString(),
                "periodId", fx.periodId.toString(),
                "returnType", "GSTR3B");

        String createResponse = mockMvc.perform(post("/api/v1/gl/gst/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(exportRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        var created = objectMapper.readTree(createResponse).at("/data");
        assertThat(created.get("status").asText()).isEqualTo("COMPLETED");
        String jobId = created.get("jobId").asText();

        mockMvc.perform(get("/api/v1/gl/gst/export/{jobId}", jobId))
                .andExpect(status().isOk());
    }

    private PostingLineRequest line(UUID accountId, String debit, String credit,
                                     boolean gstApplicable, String gstType) {
        return PostingLineRequest.builder()
                .naturalAccountValueId(accountId)
                .debitAmount(debit != null ? new BigDecimal(debit) : null)
                .creditAmount(credit != null ? new BigDecimal(credit) : null)
                .gstApplicable(gstApplicable)
                .gstType(gstType)
                .build();
    }

    private PostingRequest request(Fixture fx, LocalDate glDate, PostingLineRequest... lines) {
        return PostingRequest.builder()
                .legalEntityId(fx.legalEntityId)
                .accountingPeriodId(fx.periodId)
                .journalSourceId(fx.journalSourceId)
                .journalCategoryId(fx.journalCategoryId)
                .description("GL-27 IT journal")
                .glDate(glDate)
                .accountingDate(glDate)
                .currencyCode("INR")
                .exchangeRate(BigDecimal.ONE)
                .performedBy("it-test")
                .lines(List.of(lines))
                .build();
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
        String entityNumber = String.format("%04d", Math.abs(suffix.hashCode()) % 10000);
        String gstin = "33AABCE" + entityNumber + "F1Z5";
        createBusinessUnit(legalEntityId, "BU-" + suffix, gstin, "33");

        UUID ledgerId = createLedger("LDG-" + suffix, "THICK");
        assignPrimaryLedger(legalEntityId, ledgerId);
        List<UUID> periodIds = createPeriods(ledgerId, suffix);
        UUID naturalAcctDimId = createFinanceDimension(ledgerId, "NA-" + suffix);

        UUID cashId = createDimensionValue(naturalAcctDimId, "1100-" + suffix, "ASSET");
        UUID salesId = createDimensionValue(naturalAcctDimId, "4100-" + suffix, "REVENUE");
        UUID cgstPayableId = createDimensionValue(naturalAcctDimId, "2210-" + suffix, "LIABILITY");
        UUID sgstPayableId = createDimensionValue(naturalAcctDimId, "2220-" + suffix, "LIABILITY");

        UUID journalSourceId = journalSourceRepository.findByCode("MANUAL").orElseThrow().getId();
        UUID journalCategoryId = journalCategoryRepository.findByCode("ADJUSTMENT").orElseThrow().getId();

        return new Fixture(legalEntityId, periodIds.get(0), cashId, salesId, cgstPayableId, sgstPayableId,
                journalSourceId, journalCategoryId, gstin);
    }

    private void createBusinessUnit(UUID legalEntityId, String code, String gstin, String stateCode) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("legalEntityId", legalEntityId.toString());
        request.put("code", code);
        request.put("name", "Business Unit " + code);
        request.put("gstin", gstin);
        request.put("stateCode", stateCode);

        mockMvc.perform(post("/api/v1/gl/business-units")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private List<UUID> createPeriods(UUID ledgerId, String suffix) throws Exception {
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

        var periodsNode = objectMapper.readTree(periodsResponse).at("/data");
        List<UUID> periods = new java.util.ArrayList<>();
        for (var node : periodsNode) {
            periods.add(UUID.fromString(node.get("id").asText()));
        }
        return periods;
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

    private UUID createDimensionValue(UUID financeDimensionId, String code, String accountQualifier) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("financeDimensionId", financeDimensionId.toString());
        request.put("code", code);
        request.put("name", "Account " + code);
        request.put("accountQualifier", accountQualifier);
        request.put("normalBalance", accountQualifier.equals("ASSET") ? "DR" : "CR");
        request.put("isSummary", false);
        request.put("isPostable", true);

        String response = mockMvc.perform(post("/api/v1/gl/dimension-values")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).at("/data/id").asText());
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
        final UUID periodId;
        final UUID cashAccountId;
        final UUID salesAccountId;
        final UUID cgstPayableId;
        final UUID sgstPayableId;
        final UUID journalSourceId;
        final UUID journalCategoryId;
        final String gstin;

        Fixture(UUID legalEntityId, UUID periodId, UUID cashAccountId, UUID salesAccountId,
                UUID cgstPayableId, UUID sgstPayableId, UUID journalSourceId, UUID journalCategoryId,
                String gstin) {
            this.legalEntityId = legalEntityId;
            this.periodId = periodId;
            this.cashAccountId = cashAccountId;
            this.salesAccountId = salesAccountId;
            this.cgstPayableId = cgstPayableId;
            this.sgstPayableId = sgstPayableId;
            this.journalSourceId = journalSourceId;
            this.journalCategoryId = journalCategoryId;
            this.gstin = gstin;
        }
    }
}
