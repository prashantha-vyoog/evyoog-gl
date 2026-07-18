package com.evyoog.gl.reporting.cashflow.api;

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
class CashFlowIT {

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
    void testGetCashFlow_withPostedJournals_returnsCorrectStatement() throws Exception {
        Fixture fx = buildFixture();
        openPeriod(fx, fx.periodId);

        // Capital contribution: DR Cash 1000 / CR Equity 1000 — Financing inflow
        postingEngine.post(multiLineRequest(fx, line(fx.cashId, "1000.00", null), line(fx.equityId, null, "1000.00")));
        // Term loan raised: DR Cash 500 / CR Term Loan 500 — Financing inflow
        postingEngine.post(multiLineRequest(fx, line(fx.cashId, "500.00", null), line(fx.loanId, null, "500.00")));
        // Purchase plant: DR Plant 300 / CR Cash 300 — Investing outflow
        postingEngine.post(multiLineRequest(fx, line(fx.plantId, "300.00", null), line(fx.cashId, null, "300.00")));
        // Revenue earned on credit: DR AR 200 / CR Revenue 200 — Operating working capital use
        postingEngine.post(multiLineRequest(fx, line(fx.receivableId, "200.00", null), line(fx.revenueId, null, "200.00")));
        // Expense incurred on credit: DR Expense 150 / CR AP 150 — Operating working capital source
        postingEngine.post(multiLineRequest(fx, line(fx.expenseId, "150.00", null), line(fx.payableId, null, "150.00")));
        // Depreciation (non-cash): DR Depreciation Expense 50 / CR Accumulated Depreciation 50
        postingEngine.post(multiLineRequest(fx, line(fx.depreciationId, "50.00", null), line(fx.accumDepId, null, "50.00")));

        String response = mockMvc.perform(get("/api/v1/gl/reports/cash-flow")
                        .param("legalEntityId", fx.legalEntityId.toString())
                        .param("periodId", fx.periodId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var data = objectMapper.readTree(response).at("/data");

        assertThat(data.get("method").asText()).isEqualTo("INDIRECT");
        assertThat(data.get("operatingActivities").get("totalAmount").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(data.get("investingActivities").get("totalAmount").decimalValue()).isEqualByComparingTo("-300.00");
        assertThat(data.get("financingActivities").get("totalAmount").decimalValue()).isEqualByComparingTo("1500.00");
        assertThat(data.get("netCashChange").decimalValue()).isEqualByComparingTo("1200.00");
        assertThat(data.get("openingCashBalance").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(data.get("closingCashBalance").decimalValue()).isEqualByComparingTo("1200.00");
        assertThat(data.get("isPositiveCashFlow").asBoolean()).isTrue();
    }

    @Test
    void testGetCashFlow_netIncomeMatchesPL() throws Exception {
        Fixture fx = buildFixture();
        openPeriod(fx, fx.periodId);

        postingEngine.post(multiLineRequest(fx, line(fx.receivableId, "200.00", null), line(fx.revenueId, null, "200.00")));
        postingEngine.post(multiLineRequest(fx, line(fx.expenseId, "150.00", null), line(fx.payableId, null, "150.00")));

        String cashFlowResponse = mockMvc.perform(get("/api/v1/gl/reports/cash-flow")
                        .param("legalEntityId", fx.legalEntityId.toString())
                        .param("periodId", fx.periodId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String plResponse = mockMvc.perform(get("/api/v1/gl/reports/profit-and-loss")
                        .param("legalEntityId", fx.legalEntityId.toString())
                        .param("periodId", fx.periodId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        BigDecimal netIncome = objectMapper.readTree(plResponse).at("/data/netIncome").decimalValue();
        var operatingItems = objectMapper.readTree(cashFlowResponse).at("/data/operatingActivities/items");
        assertThat(operatingItems.get(0).get("description").asText()).isEqualTo("Net Income");
        assertThat(operatingItems.get(0).get("amount").decimalValue()).isEqualByComparingTo(netIncome);
    }

    @Test
    void testGetCashFlow_closingBalanceMatchesCashAccounts() throws Exception {
        Fixture fx = buildFixture();
        openPeriod(fx, fx.periodId);

        postingEngine.post(multiLineRequest(fx, line(fx.cashId, "700.00", null), line(fx.equityId, null, "700.00")));
        // P&L requires at least one Revenue/Expense balance to generate — a break-even
        // pair here keeps Net Income at zero without touching any cash account.
        postingEngine.post(multiLineRequest(fx, line(fx.expenseId, "10.00", null), line(fx.revenueId, null, "10.00")));

        String response = mockMvc.perform(get("/api/v1/gl/reports/cash-flow")
                        .param("legalEntityId", fx.legalEntityId.toString())
                        .param("periodId", fx.periodId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var data = objectMapper.readTree(response).at("/data");
        assertThat(data.get("closingCashBalance").decimalValue()).isEqualByComparingTo("700.00");
    }

    @Test
    void testGetCashFlow_thinLedger_returns422() throws Exception {
        Fixture fx = buildFixture("THIN");

        mockMvc.perform(get("/api/v1/gl/reports/cash-flow")
                        .param("legalEntityId", fx.legalEntityId.toString())
                        .param("periodId", fx.periodId.toString()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void testGetCashFlow_noJournals_returns404() throws Exception {
        Fixture fx = buildFixture();

        mockMvc.perform(get("/api/v1/gl/reports/cash-flow")
                        .param("legalEntityId", fx.legalEntityId.toString())
                        .param("periodId", fx.periodId.toString()))
                .andExpect(status().isNotFound());
    }

    private PostingLineRequest line(UUID accountId, String debit, String credit) {
        return PostingLineRequest.builder()
                .naturalAccountValueId(accountId)
                .debitAmount(debit != null ? new BigDecimal(debit) : null)
                .creditAmount(credit != null ? new BigDecimal(credit) : null)
                .build();
    }

    private PostingRequest multiLineRequest(Fixture fx, PostingLineRequest... lines) {
        return PostingRequest.builder()
                .legalEntityId(fx.legalEntityId)
                .accountingPeriodId(fx.periodId)
                .journalSourceId(fx.journalSourceId)
                .journalCategoryId(fx.journalCategoryId)
                .description("GL-25 IT journal")
                .glDate(LocalDate.now())
                .accountingDate(LocalDate.now())
                .currencyCode("INR")
                .exchangeRate(BigDecimal.ONE)
                .performedBy("it-test")
                .lines(List.of(lines))
                .build();
    }

    private void openPeriod(Fixture fx, UUID periodId) throws Exception {
        String createResponse = mockMvc.perform(post("/api/v1/gl/period-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "legalEntityId", fx.legalEntityId.toString(),
                                "accountingPeriodId", periodId.toString()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID statusId = UUID.fromString(objectMapper.readTree(createResponse).at("/data/id").asText());

        mockMvc.perform(post("/api/v1/gl/period-status/{id}/open", statusId))
                .andExpect(status().isOk());
    }

    private Fixture buildFixture() throws Exception {
        return buildFixture("THICK");
    }

    private Fixture buildFixture(String financeMode) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID legalEntityId = createThickEsLegalEntity(suffix);
        UUID ledgerId = createLedger("LDG-" + suffix, financeMode);
        assignPrimaryLedger(legalEntityId, ledgerId);
        List<UUID> periodIds = createPeriods(ledgerId, suffix);
        UUID naturalAcctDimId = createFinanceDimension(ledgerId, "NA-" + suffix);

        UUID cashId = createDimensionValue(naturalAcctDimId, "1100", "Cash", "ASSET", "DR");
        UUID receivableId = createDimensionValue(naturalAcctDimId, "1300", "Accounts Receivable", "ASSET", "DR");
        UUID plantId = createDimensionValue(naturalAcctDimId, "1700", "Plant and Machinery", "ASSET", "DR");
        UUID accumDepId = createDimensionValue(naturalAcctDimId, "9900", "Accumulated Depreciation", "ASSET", "CR");
        UUID payableId = createDimensionValue(naturalAcctDimId, "2100", "Accounts Payable", "LIABILITY", "CR");
        UUID loanId = createDimensionValue(naturalAcctDimId, "2600", "Term Loan", "LIABILITY", "CR");
        UUID equityId = createDimensionValue(naturalAcctDimId, "3100", "Share Capital", "EQUITY", "CR");
        UUID revenueId = createDimensionValue(naturalAcctDimId, "4100", "Sales Revenue", "REVENUE", "CR");
        UUID expenseId = createDimensionValue(naturalAcctDimId, "5100", "Operating Expense", "EXPENSE", "DR");
        UUID depreciationId = createDimensionValue(naturalAcctDimId, "5500", "Depreciation Expense", "EXPENSE", "DR");

        UUID journalSourceId = journalSourceRepository.findByCode("MANUAL").orElseThrow().getId();
        UUID journalCategoryId = journalCategoryRepository.findByCode("ADJUSTMENT").orElseThrow().getId();

        return new Fixture(legalEntityId, periodIds.get(0), cashId, receivableId, plantId, accumDepId,
                payableId, loanId, equityId, revenueId, expenseId, depreciationId,
                journalSourceId, journalCategoryId);
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

    private UUID createDimensionValue(UUID financeDimensionId, String code, String name, String accountQualifier,
                                       String normalBalance) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("financeDimensionId", financeDimensionId.toString());
        request.put("code", code);
        request.put("name", name);
        request.put("accountQualifier", accountQualifier);
        request.put("normalBalance", normalBalance);
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
        final UUID cashId;
        final UUID receivableId;
        final UUID plantId;
        final UUID accumDepId;
        final UUID payableId;
        final UUID loanId;
        final UUID equityId;
        final UUID revenueId;
        final UUID expenseId;
        final UUID depreciationId;
        final UUID journalSourceId;
        final UUID journalCategoryId;

        Fixture(UUID legalEntityId, UUID periodId, UUID cashId, UUID receivableId, UUID plantId, UUID accumDepId,
                UUID payableId, UUID loanId, UUID equityId, UUID revenueId, UUID expenseId, UUID depreciationId,
                UUID journalSourceId, UUID journalCategoryId) {
            this.legalEntityId = legalEntityId;
            this.periodId = periodId;
            this.cashId = cashId;
            this.receivableId = receivableId;
            this.plantId = plantId;
            this.accumDepId = accumDepId;
            this.payableId = payableId;
            this.loanId = loanId;
            this.equityId = equityId;
            this.revenueId = revenueId;
            this.expenseId = expenseId;
            this.depreciationId = depreciationId;
            this.journalSourceId = journalSourceId;
            this.journalCategoryId = journalCategoryId;
        }
    }
}
