package com.evyoog.gl.balance.api;

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
class AccountBalanceIT {

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
    void testGetBalances_afterPosting_returnsCorrectAmounts() throws Exception {
        Fixture fx = buildFixture();
        openPeriod(fx, fx.periodIds.get(0));

        postingEngine.post(balancedRequest(fx, fx.periodIds.get(0), fx.cashAccountId, fx.revenueAccountId));

        String response = mockMvc.perform(get("/api/v1/gl/account-balances")
                        .param("legalEntityId", fx.legalEntityId.toString())
                        .param("periodId", fx.periodIds.get(0).toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var data = objectMapper.readTree(response).at("/data");
        assertThat(data).hasSize(2);

        boolean foundCash = false;
        for (var node : data) {
            if (node.get("accountCode").asText().equals(fx.cashAccountCode)) {
                assertThat(node.get("periodToDateDr").decimalValue()).isEqualByComparingTo("100.00");
                assertThat(node.get("endingBalance").decimalValue()).isEqualByComparingTo("100.00");
                foundCash = true;
            }
        }
        assertThat(foundCash).isTrue();
    }

    @Test
    void testCarryForward_success_createsBeginningBalances() throws Exception {
        Fixture fx = buildFixture();
        UUID fromPeriodId = fx.periodIds.get(0);
        UUID toPeriodId = fx.periodIds.get(1);
        openPeriod(fx, fromPeriodId);

        postingEngine.post(balancedRequest(fx, fromPeriodId, fx.cashAccountId, fx.revenueAccountId));

        closePeriod(fx, fromPeriodId);

        String response = mockMvc.perform(post("/api/v1/gl/account-balances/carry-forward")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "legalEntityId", fx.legalEntityId.toString(),
                                "fromPeriodId", fromPeriodId.toString(),
                                "toPeriodId", toPeriodId.toString()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var data = objectMapper.readTree(response).at("/data");
        assertThat(data.get("balancesCarriedForward").asInt()).isEqualTo(1);
        assertThat(data.get("accountsCarriedForward")).extracting(n -> n.asText())
                .containsExactly(fx.cashAccountCode);

        String toBalances = mockMvc.perform(get("/api/v1/gl/account-balances")
                        .param("legalEntityId", fx.legalEntityId.toString())
                        .param("periodId", toPeriodId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var toData = objectMapper.readTree(toBalances).at("/data");
        assertThat(toData).hasSize(1);
        assertThat(toData.get(0).get("beginningBalance").decimalValue()).isEqualByComparingTo("100.00");
    }

    @Test
    void testCarryForward_pnlAccountsExcluded() throws Exception {
        Fixture fx = buildFixture();
        UUID fromPeriodId = fx.periodIds.get(0);
        UUID toPeriodId = fx.periodIds.get(1);
        openPeriod(fx, fromPeriodId);

        // cash (ASSET) debited, revenue (REVENUE) credited — only cash should carry forward
        postingEngine.post(balancedRequest(fx, fromPeriodId, fx.cashAccountId, fx.revenueAccountId));

        closePeriod(fx, fromPeriodId);

        mockMvc.perform(post("/api/v1/gl/account-balances/carry-forward")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "legalEntityId", fx.legalEntityId.toString(),
                                "fromPeriodId", fromPeriodId.toString(),
                                "toPeriodId", toPeriodId.toString()))))
                .andExpect(status().isOk());

        String toBalances = mockMvc.perform(get("/api/v1/gl/account-balances")
                        .param("legalEntityId", fx.legalEntityId.toString())
                        .param("periodId", toPeriodId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var toData = objectMapper.readTree(toBalances).at("/data");
        assertThat(toData).hasSize(1);
        assertThat(toData.get(0).get("accountCode").asText()).isEqualTo(fx.cashAccountCode);
    }

    private PostingRequest balancedRequest(Fixture fx, UUID periodId, UUID debitAccountId, UUID creditAccountId) {
        return PostingRequest.builder()
                .legalEntityId(fx.legalEntityId)
                .accountingPeriodId(periodId)
                .journalSourceId(fx.journalSourceId)
                .journalCategoryId(fx.journalCategoryId)
                .description("GL-21 IT journal")
                .glDate(LocalDate.now())
                .accountingDate(LocalDate.now())
                .currencyCode("INR")
                .exchangeRate(BigDecimal.ONE)
                .performedBy("it-test")
                .lines(List.of(
                        PostingLineRequest.builder().naturalAccountValueId(debitAccountId)
                                .debitAmount(new BigDecimal("100.00")).build(),
                        PostingLineRequest.builder().naturalAccountValueId(creditAccountId)
                                .creditAmount(new BigDecimal("100.00")).build()))
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

    private void closePeriod(Fixture fx, UUID periodId) throws Exception {
        String listResponse = mockMvc.perform(get("/api/v1/gl/period-status")
                        .param("legalEntityId", fx.legalEntityId.toString())
                        .param("accountingPeriodId", periodId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID statusId = UUID.fromString(objectMapper.readTree(listResponse).at("/data/0/id").asText());

        mockMvc.perform(post("/api/v1/gl/period-status/{id}/close", statusId))
                .andExpect(status().isOk());
    }

    private Fixture buildFixture() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID legalEntityId = createThickEsLegalEntity(suffix);
        UUID ledgerId = createLedger("LDG-" + suffix, "THICK");
        assignPrimaryLedger(legalEntityId, ledgerId);
        List<UUID> periodIds = createPeriods(ledgerId, suffix);
        UUID naturalAcctDimId = createFinanceDimension(ledgerId, "NA-" + suffix);
        String cashAccountCode = "1000-" + suffix;
        UUID cashAccountId = createDimensionValue(naturalAcctDimId, cashAccountCode, "ASSET");
        UUID revenueAccountId = createDimensionValue(naturalAcctDimId, "4000-" + suffix, "REVENUE");
        UUID journalSourceId = journalSourceRepository.findByCode("MANUAL").orElseThrow().getId();
        UUID journalCategoryId = journalCategoryRepository.findByCode("ADJUSTMENT").orElseThrow().getId();

        return new Fixture(legalEntityId, ledgerId, periodIds, naturalAcctDimId,
                cashAccountId, cashAccountCode, revenueAccountId, journalSourceId, journalCategoryId);
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
        final UUID ledgerId;
        final List<UUID> periodIds;
        final UUID naturalAcctDimId;
        final UUID cashAccountId;
        final String cashAccountCode;
        final UUID revenueAccountId;
        final UUID journalSourceId;
        final UUID journalCategoryId;

        Fixture(UUID legalEntityId, UUID ledgerId, List<UUID> periodIds, UUID naturalAcctDimId,
                UUID cashAccountId, String cashAccountCode, UUID revenueAccountId,
                UUID journalSourceId, UUID journalCategoryId) {
            this.legalEntityId = legalEntityId;
            this.ledgerId = ledgerId;
            this.periodIds = periodIds;
            this.naturalAcctDimId = naturalAcctDimId;
            this.cashAccountId = cashAccountId;
            this.cashAccountCode = cashAccountCode;
            this.revenueAccountId = revenueAccountId;
            this.journalSourceId = journalSourceId;
            this.journalCategoryId = journalCategoryId;
        }
    }
}
