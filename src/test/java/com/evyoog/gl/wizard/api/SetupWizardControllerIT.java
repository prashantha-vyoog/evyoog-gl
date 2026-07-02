package com.evyoog.gl.wizard.api;

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
class SetupWizardControllerIT {

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
    void runWizard_forWorkspaceContext_shouldProvisionFullHierarchy() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID contextId = createConsumptionContext("CTX-" + suffix, "WORKSPACE");

        Map<String, Object> answers = Map.of(
                "contextId", contextId.toString(),
                "companyName", "Coimbatore Manufacturing Pvt Ltd",
                "legalStructure", "PRIVATE_LIMITED",
                "businessType", "MANUFACTURING",
                "states", new String[]{"TAMIL_NADU", "MAHARASHTRA"},
                "startMonth", 4,
                "startYear", 2025,
                "hasOwnCoa", false,
                "industryTemplate", "MANUFACTURING");

        mockMvc.perform(post("/api/v1/gl/setup-wizard/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(answers)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.esMode").value("THICK_ES"))
                .andExpect(jsonPath("$.data.accountingStandard").value("IND_AS"))
                .andExpect(jsonPath("$.data.businessUnits.length()").value(2));

        mockMvc.perform(get("/api/v1/gl/setup-wizard/status/{contextId}", contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isCompleted").value(true));
    }

    @Test
    void runWizard_whenAlreadyRun_shouldReturn409() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID contextId = createConsumptionContext("CTX-" + suffix, "WORKSPACE");

        Map<String, Object> answers = Map.of(
                "contextId", contextId.toString(),
                "companyName", "Second Run Co",
                "legalStructure", "LLP",
                "businessType", "SERVICES",
                "states", new String[]{"KARNATAKA"},
                "startMonth", 4,
                "startYear", 2025,
                "hasOwnCoa", true);

        mockMvc.perform(post("/api/v1/gl/setup-wizard/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(answers)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/gl/setup-wizard/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(answers)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WIZARD_ALREADY_RUN"));
    }

    @Test
    void runWizard_forSessionContextWithoutSessionPurpose_shouldReturn400() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID contextId = createConsumptionContext("CTX-" + suffix, "SESSION");

        Map<String, Object> answers = Map.of(
                "contextId", contextId.toString(),
                "companyName", "Trial Session Co",
                "legalStructure", "PRIVATE_LIMITED",
                "businessType", "SIMPLE",
                "states", new String[]{"DELHI"},
                "startMonth", 4,
                "startYear", 2025);

        mockMvc.perform(post("/api/v1/gl/setup-wizard/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(answers)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SESSION_PURPOSE_REQUIRED"));
    }

    @Test
    void statesEndpoint_shouldReturnAllIndianStates() throws Exception {
        mockMvc.perform(get("/api/v1/gl/setup-wizard/states"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(31));
    }

    private UUID createConsumptionContext(String code, String segmentType) throws Exception {
        Map<String, Object> request = Map.of(
                "segmentType", segmentType,
                "code", code,
                "name", "Wizard Test Context");

        String response = mockMvc.perform(post("/api/v1/gl/consumption-contexts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).at("/data/id").asText());
    }
}
