package com.evyoog.gl.enterprise.api;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class EnterpriseSetupIT {

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
    void fullHierarchy_createGoldenPath_shouldSucceed() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        UUID contextId = createConsumptionContext("CTX-" + suffix);
        UUID businessGroupId = createBusinessGroup(contextId, "BG-" + suffix, "THICK_ES");
        UUID legalEntityId = createLegalEntity(businessGroupId, "LE-" + suffix);
        UUID businessUnitId = createBusinessUnit(legalEntityId, "BU-" + suffix, "33AABCE1234F1Z5");
        UUID inventoryOrgId = createInventoryOrganisation(businessUnitId, "IO-" + suffix);
        createSubInventory(inventoryOrgId, "SI-" + suffix);

        mockMvc.perform(get("/api/v1/gl/legal-entities/{id}", legalEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessGroupId").value(businessGroupId.toString()));

        mockMvc.perform(get("/api/v1/gl/business-units").param("legalEntityId", legalEntityId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].gstin").value("33AABCE1234F1Z5"))
                .andExpect(jsonPath("$.data[0].stateCode").value("33"));
    }

    @Test
    void createLegalEntity_whenThinEsAlreadyHasOne_shouldReturn409() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID contextId = createConsumptionContext("CTX-" + suffix);
        UUID businessGroupId = createBusinessGroup(contextId, "BG-" + suffix, "THIN_ES");
        createLegalEntity(businessGroupId, "LE1-" + suffix);

        Map<String, Object> secondLe = Map.of(
                "businessGroupId", businessGroupId.toString(),
                "code", "LE2-" + suffix,
                "name", "Second Legal Entity");

        mockMvc.perform(post("/api/v1/gl/legal-entities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondLe)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("THIN_ES_LE_LIMIT"));
    }

    @Test
    void createBusinessUnit_whenGstinInvalid_shouldReturn400() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID contextId = createConsumptionContext("CTX-" + suffix);
        UUID businessGroupId = createBusinessGroup(contextId, "BG-" + suffix, "THICK_ES");
        UUID legalEntityId = createLegalEntity(businessGroupId, "LE-" + suffix);

        Map<String, Object> invalidBu = Map.of(
                "legalEntityId", legalEntityId.toString(),
                "code", "BU-" + suffix,
                "name", "Invalid GSTIN Unit",
                "gstin", "NOT-A-GSTIN");

        mockMvc.perform(post("/api/v1/gl/business-units")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidBu)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_GSTIN"));
    }

    @Test
    void createBusinessGroup_whenDuplicateCodeInContext_shouldReturn409() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID contextId = createConsumptionContext("CTX-" + suffix);
        createBusinessGroup(contextId, "BG-" + suffix, "THICK_ES");

        Map<String, Object> duplicate = Map.of(
                "consumptionContextId", contextId.toString(),
                "code", "BG-" + suffix,
                "name", "Duplicate Group",
                "esMode", "THICK_ES",
                "defaultCurrency", "INR");

        mockMvc.perform(post("/api/v1/gl/business-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));
    }

    @Test
    void auditLog_afterCreate_shouldContainCreateEntry() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID contextId = createConsumptionContext("CTX-" + suffix);

        mockMvc.perform(get("/api/v1/gl/audit-log")
                        .param("entityName", "consumption_context")
                        .param("entityId", contextId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].action").value("CREATE"))
                .andExpect(jsonPath("$.data.content[0].entityId").value(contextId.toString()));
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
                .andExpect(jsonPath("$.data.code").value(code))
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
                .andExpect(jsonPath("$.data.code").value(code))
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).at("/data/id").asText());
    }

    private UUID createLegalEntity(UUID businessGroupId, String code) throws Exception {
        Map<String, Object> request = Map.of(
                "businessGroupId", businessGroupId.toString(),
                "code", code,
                "name", "Coimbatore Plant Legal Entity");

        String response = mockMvc.perform(post("/api/v1/gl/legal-entities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value(code))
                .andExpect(jsonPath("$.data.accountingStandard").value("IND_AS"))
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).at("/data/id").asText());
    }

    private UUID createBusinessUnit(UUID legalEntityId, String code, String gstin) throws Exception {
        Map<String, Object> request = Map.of(
                "legalEntityId", legalEntityId.toString(),
                "code", code,
                "name", "Coimbatore Plant",
                "gstin", gstin);

        String response = mockMvc.perform(post("/api/v1/gl/business-units")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.gstin").value(gstin))
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).at("/data/id").asText());
    }

    private UUID createInventoryOrganisation(UUID businessUnitId, String code) throws Exception {
        Map<String, Object> request = Map.of(
                "businessUnitId", businessUnitId.toString(),
                "code", code,
                "name", "Main Warehouse");

        String response = mockMvc.perform(post("/api/v1/gl/inventory-organisations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).at("/data/id").asText());
    }

    private UUID createSubInventory(UUID inventoryOrganisationId, String code) throws Exception {
        Map<String, Object> request = Map.of(
                "inventoryOrganisationId", inventoryOrganisationId.toString(),
                "code", code,
                "name", "Raw Materials Bin");

        String response = mockMvc.perform(post("/api/v1/gl/sub-inventories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).at("/data/id").asText());
    }
}
