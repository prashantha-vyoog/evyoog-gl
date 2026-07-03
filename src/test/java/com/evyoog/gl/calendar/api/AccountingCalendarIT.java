package com.evyoog.gl.calendar.api;

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
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AccountingCalendarIT {

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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createCalendar_success() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID ledgerId = createLedger("LDG-" + suffix);

        Map<String, Object> request = new HashMap<>();
        request.put("ledgerId", ledgerId.toString());
        request.put("name", "FY Calendar");
        request.put("initialFiscalYear", 2025);

        mockMvc.perform(post("/api/v1/gl/accounting-calendars")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fiscalYearStartMonth").value(4))
                .andExpect(jsonPath("$.data.fiscalYearStartDay").value(1))
                .andExpect(jsonPath("$.data.periodType").value("MONTHLY"))
                .andExpect(jsonPath("$.data.periodsPerYear").value(12))
                .andExpect(jsonPath("$.data.generatedPeriodCount").value(12))
                .andExpect(jsonPath("$.data.currentFiscalYear").value("2025-26"))
                .andExpect(jsonPath("$.data.ledgerId").value(ledgerId.toString()));
    }

    @Test
    void createCalendar_duplicate_returns409() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID ledgerId = createLedger("LDG-" + suffix);
        createCalendarFor(ledgerId, 2025);

        Map<String, Object> request = new HashMap<>();
        request.put("ledgerId", ledgerId.toString());
        request.put("name", "Second Calendar");
        request.put("initialFiscalYear", 2025);

        mockMvc.perform(post("/api/v1/gl/accounting-calendars")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CALENDAR_EXISTS"));
    }

    @Test
    void getCalendarByLedgerId() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID ledgerId = createLedger("LDG-" + suffix);
        createCalendarFor(ledgerId, 2025);

        // currentFiscalYear reflects the fiscal year containing *today*, not the year the
        // calendar was initially generated for, since gl.accounting_calendar has no notion
        // of a stored "current year" until GL-09 introduces real accounting_period rows.
        java.time.LocalDate today = java.time.LocalDate.now();
        int fiscalStartYear = today.isBefore(java.time.LocalDate.of(today.getYear(), 4, 1))
                ? today.getYear() - 1 : today.getYear();
        String expectedFiscalYear = fiscalStartYear + "-" + String.valueOf(fiscalStartYear + 1).substring(2);

        mockMvc.perform(get("/api/v1/gl/accounting-calendars").param("ledgerId", ledgerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ledgerId").value(ledgerId.toString()))
                .andExpect(jsonPath("$.data.currentFiscalYear").value(expectedFiscalYear));
    }

    private void createCalendarFor(UUID ledgerId, int initialFiscalYear) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("ledgerId", ledgerId.toString());
        request.put("name", "FY Calendar");
        request.put("initialFiscalYear", initialFiscalYear);

        mockMvc.perform(post("/api/v1/gl/accounting-calendars")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private UUID createLedger(String code) throws Exception {
        Map<String, Object> request = Map.of(
                "code", code,
                "name", "Ledger " + code,
                "financeMode", "THICK");

        String response = mockMvc.perform(post("/api/v1/gl/ledgers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).at("/data/id").asText());
    }
}
