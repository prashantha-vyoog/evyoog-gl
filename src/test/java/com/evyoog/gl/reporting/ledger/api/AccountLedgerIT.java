package com.evyoog.gl.reporting.ledger.api;

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
class AccountLedgerIT {

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
    void testAccountLedger_afterPostingJournals_correctRunningBalance() throws Exception {
        Fixture fx = buildFixture();
        openPeriod(fx, fx.periodId);

        postingEngine.post(request(fx, LocalDate.of(2025, 4, 5),
                line(fx.cashAccountId, "500.00", null),
                line(fx.equityAccountId, null, "500.00")));
        postingEngine.post(request(fx, LocalDate.of(2025, 4, 10),
                line(fx.equityAccountId, "100.00", null),
                line(fx.cashAccountId, null, "100.00")));

        String response = mockMvc.perform(get("/api/v1/gl/reports/account-ledger")
                        .param("legalEntityId", fx.legalEntityId.toString())
                        .param("accountingPeriodId", fx.periodId.toString())
                        .param("naturalAccountValueId", fx.cashAccountId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var data = objectMapper.readTree(response).at("/data");

        assertThat(data.get("openingBalance").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(data.get("entries")).hasSize(2);
        assertThat(data.get("entries").get(0).get("runningBalance").decimalValue()).isEqualByComparingTo("500.00");
        assertThat(data.get("entries").get(1).get("runningBalance").decimalValue()).isEqualByComparingTo("400.00");
        assertThat(data.get("totalDebits").decimalValue()).isEqualByComparingTo("500.00");
        assertThat(data.get("totalCredits").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(data.get("closingBalance").decimalValue()).isEqualByComparingTo("400.00");
    }

    @Test
    void testAccountLedger_multipleJournals_sortedByDate() throws Exception {
        Fixture fx = buildFixture();
        openPeriod(fx, fx.periodId);

        // Post the later-dated journal first to prove sorting isn't insertion-order dependent.
        postingEngine.post(request(fx, LocalDate.of(2025, 4, 20),
                line(fx.cashAccountId, "50.00", null),
                line(fx.equityAccountId, null, "50.00")));
        postingEngine.post(request(fx, LocalDate.of(2025, 4, 1),
                line(fx.cashAccountId, "200.00", null),
                line(fx.equityAccountId, null, "200.00")));

        String response = mockMvc.perform(get("/api/v1/gl/reports/account-ledger")
                        .param("legalEntityId", fx.legalEntityId.toString())
                        .param("accountingPeriodId", fx.periodId.toString())
                        .param("naturalAccountValueId", fx.cashAccountId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var entries = objectMapper.readTree(response).at("/data/entries");

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).get("glDate").asText()).isEqualTo("2025-04-01");
        assertThat(entries.get(1).get("glDate").asText()).isEqualTo("2025-04-20");
    }

    @Test
    void testJournalListing_filterByStatus() throws Exception {
        Fixture fx = buildFixture();
        openPeriod(fx, fx.periodId);

        postingEngine.post(request(fx, LocalDate.of(2025, 4, 5),
                line(fx.cashAccountId, "100.00", null),
                line(fx.equityAccountId, null, "100.00")));

        String postedResponse = mockMvc.perform(get("/api/v1/gl/reports/journal-listing")
                        .param("legalEntityId", fx.legalEntityId.toString())
                        .param("status", "POSTED"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var postedContent = objectMapper.readTree(postedResponse).at("/data/content");
        assertThat(postedContent).hasSize(1);
        assertThat(postedContent.get(0).get("status").asText()).isEqualTo("POSTED");

        String draftResponse = mockMvc.perform(get("/api/v1/gl/reports/journal-listing")
                        .param("legalEntityId", fx.legalEntityId.toString())
                        .param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var draftContent = objectMapper.readTree(draftResponse).at("/data/content");
        assertThat(draftContent).isEmpty();
    }

    @Test
    void testJournalListing_filterByPeriod() throws Exception {
        Fixture fx = buildFixture();
        openPeriod(fx, fx.periodId);

        postingEngine.post(request(fx, LocalDate.of(2025, 4, 5),
                line(fx.cashAccountId, "100.00", null),
                line(fx.equityAccountId, null, "100.00")));

        String matchingResponse = mockMvc.perform(get("/api/v1/gl/reports/journal-listing")
                        .param("legalEntityId", fx.legalEntityId.toString())
                        .param("periodId", fx.periodId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(matchingResponse).at("/data/content")).hasSize(1);

        String otherPeriodResponse = mockMvc.perform(get("/api/v1/gl/reports/journal-listing")
                        .param("legalEntityId", fx.legalEntityId.toString())
                        .param("periodId", fx.otherPeriodId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(otherPeriodResponse).at("/data/content")).isEmpty();
    }

    private PostingLineRequest line(UUID accountId, String debit, String credit) {
        return PostingLineRequest.builder()
                .naturalAccountValueId(accountId)
                .debitAmount(debit != null ? new BigDecimal(debit) : null)
                .creditAmount(credit != null ? new BigDecimal(credit) : null)
                .build();
    }

    private PostingRequest request(Fixture fx, LocalDate glDate, PostingLineRequest... lines) {
        return PostingRequest.builder()
                .legalEntityId(fx.legalEntityId)
                .accountingPeriodId(fx.periodId)
                .journalSourceId(fx.journalSourceId)
                .journalCategoryId(fx.journalCategoryId)
                .description("GL-26 IT journal")
                .glDate(glDate)
                .accountingDate(glDate)
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
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID legalEntityId = createThickEsLegalEntity(suffix);
        UUID ledgerId = createLedger("LDG-" + suffix, "THICK");
        assignPrimaryLedger(legalEntityId, ledgerId);
        List<UUID> periodIds = createPeriods(ledgerId, suffix);
        UUID naturalAcctDimId = createFinanceDimension(ledgerId, "NA-" + suffix);

        UUID cashId = createDimensionValue(naturalAcctDimId, "1100-" + suffix, "ASSET", "DR", null, false);
        UUID equityId = createDimensionValue(naturalAcctDimId, "3000-" + suffix, "EQUITY", "CR", null, false);

        UUID journalSourceId = journalSourceRepository.findByCode("MANUAL").orElseThrow().getId();
        UUID journalCategoryId = journalCategoryRepository.findByCode("ADJUSTMENT").orElseThrow().getId();

        return new Fixture(legalEntityId, periodIds.get(0), periodIds.get(1),
                cashId, equityId, journalSourceId, journalCategoryId);
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

    private UUID createDimensionValue(UUID financeDimensionId, String code, String accountQualifier,
                                       String normalBalance, UUID parentValueId, boolean isSummary) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("financeDimensionId", financeDimensionId.toString());
        request.put("code", code);
        request.put("name", "Account " + code);
        request.put("accountQualifier", accountQualifier);
        request.put("normalBalance", normalBalance);
        request.put("isSummary", isSummary);
        request.put("isPostable", !isSummary);
        if (parentValueId != null) {
            request.put("parentValueId", parentValueId.toString());
        }

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
        final UUID otherPeriodId;
        final UUID cashAccountId;
        final UUID equityAccountId;
        final UUID journalSourceId;
        final UUID journalCategoryId;

        Fixture(UUID legalEntityId, UUID periodId, UUID otherPeriodId,
                UUID cashAccountId, UUID equityAccountId,
                UUID journalSourceId, UUID journalCategoryId) {
            this.legalEntityId = legalEntityId;
            this.periodId = periodId;
            this.otherPeriodId = otherPeriodId;
            this.cashAccountId = cashAccountId;
            this.equityAccountId = equityAccountId;
            this.journalSourceId = journalSourceId;
            this.journalCategoryId = journalCategoryId;
        }
    }
}
