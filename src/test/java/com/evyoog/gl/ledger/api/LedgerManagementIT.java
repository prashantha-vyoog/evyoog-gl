package com.evyoog.gl.ledger.api;

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

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class LedgerManagementIT {

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
    void createAndRetrieveLedger_shouldSucceed() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID ledgerId = createLedger("LDG-" + suffix, "THICK");

        mockMvc.perform(get("/api/v1/gl/ledgers/{id}", ledgerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.financeMode").value("THICK"))
                .andExpect(jsonPath("$.data.functionalCurrency").value("INR"));
    }

    @Test
    void financeModeUpgrade_thinToThick_shouldSucceed() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID ledgerId = createLedger("LDG-" + suffix, "THIN");

        mockMvc.perform(patch("/api/v1/gl/ledgers/{id}/finance-mode", ledgerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("financeMode", "THICK"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.financeMode").value("THICK"));
    }

    @Test
    void financeModeUpgrade_thickToThin_shouldReturn409() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID ledgerId = createLedger("LDG-" + suffix, "THICK");

        mockMvc.perform(patch("/api/v1/gl/ledgers/{id}/finance-mode", ledgerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("financeMode", "THIN"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FINANCE_MODE_DOWNGRADE"));
    }

    @Test
    void assignPrimaryLedger_shouldSucceed() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID legalEntityId = createThickEsLegalEntity(suffix);
        UUID ledgerId = createLedger("LDG-" + suffix, "THICK");

        mockMvc.perform(post("/api/v1/gl/legal-entity-ledgers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "legalEntityId", legalEntityId.toString(),
                                "ledgerId", ledgerId.toString(),
                                "ledgerCategory", "PRIMARY"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.ledgerCategory").value("PRIMARY"))
                .andExpect(jsonPath("$.data.financeMode").value("THICK"));
    }

    @Test
    void assignSecondPrimaryLedger_shouldReturn409() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID legalEntityId = createThickEsLegalEntity(suffix);
        UUID firstLedgerId = createLedger("LDG1-" + suffix, "THICK");
        UUID secondLedgerId = createLedger("LDG2-" + suffix, "THICK");

        mockMvc.perform(post("/api/v1/gl/legal-entity-ledgers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "legalEntityId", legalEntityId.toString(),
                                "ledgerId", firstLedgerId.toString(),
                                "ledgerCategory", "PRIMARY"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/gl/legal-entity-ledgers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "legalEntityId", legalEntityId.toString(),
                                "ledgerId", secondLedgerId.toString(),
                                "ledgerCategory", "PRIMARY"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRIMARY_LEDGER_EXISTS"));
    }

    @Test
    void assignThickLedgerToThinEs_shouldReturn409() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID legalEntityId = createThinEsLegalEntity(suffix);
        UUID ledgerId = createLedger("LDG-" + suffix, "THICK");

        mockMvc.perform(post("/api/v1/gl/legal-entity-ledgers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "legalEntityId", legalEntityId.toString(),
                                "ledgerId", ledgerId.toString(),
                                "ledgerCategory", "PRIMARY"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("THIN_ES_THICK_MODE_CONFLICT"));
    }

    @Test
    void deactivateLedger_shouldSoftDelete() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID ledgerId = createLedger("LDG-" + suffix, "THICK");

        mockMvc.perform(delete("/api/v1/gl/ledgers/{id}", ledgerId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/gl/ledgers/{id}", ledgerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(false));
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
                .andExpect(jsonPath("$.data.code").value(code))
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).at("/data/id").asText());
    }

    private UUID createThickEsLegalEntity(String suffix) throws Exception {
        return createLegalEntity(suffix, "THICK_ES");
    }

    private UUID createThinEsLegalEntity(String suffix) throws Exception {
        return createLegalEntity(suffix, "THIN_ES");
    }

    private UUID createLegalEntity(String suffix, String esMode) throws Exception {
        UUID contextId = createConsumptionContext("CTX-" + suffix);
        UUID businessGroupId = createBusinessGroup(contextId, "BG-" + suffix, esMode);

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
}
