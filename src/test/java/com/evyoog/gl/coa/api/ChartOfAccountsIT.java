package com.evyoog.gl.coa.api;

import com.fasterxml.jackson.databind.JsonNode;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ChartOfAccountsIT {

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
    void applyManufacturingTemplate_creates34Accounts() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID ledgerId = createLedger("LDG-" + suffix, "THICK");
        createDimension(ledgerId, "NA-" + suffix, "Natural Account", "NATURAL_ACCOUNT");
        UUID templateId = findTemplateIdByCode("MANUFACTURING_THICK");

        mockMvc.perform(post("/api/v1/gl/provisioning-templates/{id}/apply", templateId)
                        .param("ledgerId", ledgerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountsCreated").value(34));
    }

    @Test
    void hierarchyEndpoint_returnsNestedTree() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID ledgerId = createLedger("LDG-" + suffix, "THICK");
        createDimension(ledgerId, "NA-" + suffix, "Natural Account", "NATURAL_ACCOUNT");
        UUID templateId = findTemplateIdByCode("MANUFACTURING_THICK");
        applyTemplate(templateId, ledgerId);

        String response = mockMvc.perform(get("/api/v1/gl/chart-of-accounts").param("ledgerId", ledgerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(34))
                .andReturn().getResponse().getContentAsString();

        JsonNode roots = objectMapper.readTree(response).at("/data/accounts");
        assertThat(roots).hasSize(5);
        JsonNode assetsNode = null;
        for (JsonNode node : roots) {
            if (node.get("code").asText().equals("1000")) {
                assetsNode = node;
            }
        }
        assertThat(assetsNode).isNotNull();
        assertThat(assetsNode.get("children")).isNotEmpty();
    }

    @Test
    void postableEndpoint_excludesSummaryAndExpired() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID ledgerId = createLedger("LDG-" + suffix, "THICK");
        createDimension(ledgerId, "NA-" + suffix, "Natural Account", "NATURAL_ACCOUNT");
        UUID templateId = findTemplateIdByCode("MANUFACTURING_THICK");
        applyTemplate(templateId, ledgerId);

        String response = mockMvc.perform(get("/api/v1/gl/chart-of-accounts/postable").param("ledgerId", ledgerId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(response).at("/data");
        assertThat(data).hasSize(26);
        for (JsonNode account : data) {
            assertThat(account.get("code").asText()).isNotEqualTo("1000");
            assertThat(account.get("isSummary").asBoolean()).isFalse();
        }
    }

    @Test
    void searchEndpoint_findsAccountByCode() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID ledgerId = createLedger("LDG-" + suffix, "THICK");
        createDimension(ledgerId, "NA-" + suffix, "Natural Account", "NATURAL_ACCOUNT");
        UUID templateId = findTemplateIdByCode("MANUFACTURING_THICK");
        applyTemplate(templateId, ledgerId);

        mockMvc.perform(get("/api/v1/gl/chart-of-accounts/search")
                        .param("ledgerId", ledgerId.toString())
                        .param("query", "1110"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("1110"));

        mockMvc.perform(get("/api/v1/gl/chart-of-accounts/search")
                        .param("ledgerId", ledgerId.toString())
                        .param("query", "cash"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void intercompanyValue_requiresCounterparty() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID ledgerId = createLedger("LDG-" + suffix, "THICK");
        UUID icDimensionId = createDimension(ledgerId, "IC-" + suffix, "Intercompany", "INTERCOMPANY");

        Map<String, Object> request = new HashMap<>();
        request.put("financeDimensionId", icDimensionId.toString());
        request.put("code", "IC-001-" + suffix);
        request.put("name", "Intercompany Value");

        mockMvc.perform(post("/api/v1/gl/dimension-values")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IC_COUNTERPARTY_REQUIRED"));
    }

    private void applyTemplate(UUID templateId, UUID ledgerId) throws Exception {
        mockMvc.perform(post("/api/v1/gl/provisioning-templates/{id}/apply", templateId)
                        .param("ledgerId", ledgerId.toString()))
                .andExpect(status().isOk());
    }

    private UUID findTemplateIdByCode(String code) throws Exception {
        String response = mockMvc.perform(get("/api/v1/gl/provisioning-templates"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode templates = objectMapper.readTree(response).at("/data");
        for (JsonNode template : templates) {
            if (template.get("code").asText().equals(code)) {
                return UUID.fromString(template.get("id").asText());
            }
        }
        throw new IllegalStateException("Template not found: " + code);
    }

    private UUID createDimension(UUID ledgerId, String code, String name, String dimensionType) throws Exception {
        Map<String, Object> request = Map.of(
                "ledgerId", ledgerId.toString(),
                "code", code,
                "name", name,
                "dimensionType", dimensionType);

        String response = mockMvc.perform(post("/api/v1/gl/finance-dimensions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).at("/data/id").asText());
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
}
