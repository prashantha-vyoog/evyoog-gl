package com.evyoog.gl.auth.api;

import com.evyoog.gl.auth.domain.Role;
import com.evyoog.gl.auth.repository.RoleRepository;
import com.evyoog.gl.auth.repository.UserRepository;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers AUTH-01 end to end: login/refresh/logout/me, protected-endpoint enforcement,
 * user management, role management, and approval policy CRUD. Kept as a single IT class
 * (rather than one per controller) to avoid piling more Testcontainers Postgres instances
 * onto an already sizeable suite — each additional @SpringBootTest IT class here spins up
 * its own container for the lifetime of the JVM fork.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AuthControllerIT {

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
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;

    private static final String TEST_PASSWORD = "Passw0rd!2026";

    // ── auth: login / refresh / logout / me ─────────────────────────────────

    @Test
    void testLogin_success_returnsJwt() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID legalEntityId = createLegalEntity(suffix);
        String email = "manager-" + suffix + "@evyoog.test";
        createUserWithRole(email, "GL_MANAGER", legalEntityId);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "password", TEST_PASSWORD, "legalEntityId", legalEntityId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.permissions").isArray())
                .andExpect(jsonPath("$.data.mustChangePwd").value(true));
    }

    @Test
    void testLogin_wrongPassword_returns401() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID legalEntityId = createLegalEntity(suffix);
        String email = "wrongpwd-" + suffix + "@evyoog.test";
        createUserWithRole(email, "GL_VIEWER", legalEntityId);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "password", "totally-wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_FAILED"));
    }

    @Test
    void testRefresh_success_rotatesTokens() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID legalEntityId = createLegalEntity(suffix);
        String email = "refresh-" + suffix + "@evyoog.test";
        createUserWithRole(email, "GL_VIEWER", legalEntityId);

        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "password", TEST_PASSWORD, "legalEntityId", legalEntityId.toString()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String oldRefreshToken = objectMapper.readTree(loginResponse).at("/data/refreshToken").asText();

        String refreshResponse = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refreshToken", oldRefreshToken, "legalEntityId", legalEntityId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andReturn().getResponse().getContentAsString();
        String newRefreshToken = objectMapper.readTree(refreshResponse).at("/data/refreshToken").asText();

        assertThat(newRefreshToken).isNotEqualTo(oldRefreshToken);

        // old token is now revoked
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", oldRefreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));
    }

    @Test
    void testLogout_revokesToken() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID legalEntityId = createLegalEntity(suffix);
        String email = "logout-" + suffix + "@evyoog.test";
        createUserWithRole(email, "GL_VIEWER", legalEntityId);

        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "password", TEST_PASSWORD, "legalEntityId", legalEntityId.toString()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String refreshToken = objectMapper.readTree(loginResponse).at("/data/refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));
    }

    @Test
    void testGetMe_withValidToken_returnsProfile() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID legalEntityId = createLegalEntity(suffix);
        String email = "me-" + suffix + "@evyoog.test";
        createUserWithRole(email, "GL_MANAGER", legalEntityId);
        String accessToken = loginAndGetAccessToken(email, TEST_PASSWORD, legalEntityId);

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.roles[0].roleCode").value("GL_MANAGER"))
                .andExpect(jsonPath("$.data.permissions").isArray());
    }

    // ── protected endpoint enforcement ───────────────────────────────────────

    @Test
    void testProtectedEndpoint_withoutToken_returns401() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID legalEntityId = createLegalEntity(suffix);

        mockMvc.perform(get("/api/v1/gl/journals")
                        .header("Authorization", "Bearer not-a-real-token")
                        .param("legalEntityId", legalEntityId.toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void testProtectedEndpoint_withValidToken_returns200() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID legalEntityId = createLegalEntity(suffix);

        // Default superuser token is auto-merged by TestJwtMockMvcCustomizer.
        mockMvc.perform(get("/api/v1/gl/journals").param("legalEntityId", legalEntityId.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void testProtectedEndpoint_insufficientPermission_returns403() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID legalEntityId = createLegalEntity(suffix);
        String email = "viewer-" + suffix + "@evyoog.test";
        createUserWithRole(email, "GL_VIEWER", legalEntityId);
        String accessToken = loginAndGetAccessToken(email, TEST_PASSWORD, legalEntityId);

        // @PreAuthorize is evaluated before the service looks the journal up, so a
        // GL_VIEWER (no gl:journal:submit) is blocked with 403 regardless of whether
        // this id exists — no request body needed, so no 400 from bean validation.
        mockMvc.perform(post("/api/v1/gl/journals/{id}/submit", UUID.randomUUID())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    // ── user management (SYS_ADMIN only) ────────────────────────────────────

    @Test
    void testCreateUser_asSysAdmin_success() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID legalEntityId = createLegalEntity(suffix);

        UUID userId = createUserWithRole("newuser-" + suffix + "@evyoog.test", "GL_VIEWER", legalEntityId);

        mockMvc.perform(get("/api/v1/auth/users/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePwd").value(true));
    }

    @Test
    void testCreateUser_asNonAdmin_returns403() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID legalEntityId = createLegalEntity(suffix);
        String email = "notadmin-" + suffix + "@evyoog.test";
        createUserWithRole(email, "GL_VIEWER", legalEntityId);
        String accessToken = loginAndGetAccessToken(email, TEST_PASSWORD, legalEntityId);

        Map<String, Object> request = Map.of(
                "email", "shouldnotwork-" + suffix + "@evyoog.test",
                "fullName", "Should Not Work",
                "password", TEST_PASSWORD,
                "roleId", roleRepository.findByCode("GL_VIEWER").orElseThrow().getId().toString(),
                "legalEntityId", legalEntityId.toString());

        mockMvc.perform(post("/api/v1/auth/users")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void testAssignRole_asSysAdmin_success() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID legalEntityId = createLegalEntity(suffix);
        UUID userId = createUserWithRole("assignrole-" + suffix + "@evyoog.test", "GL_VIEWER", legalEntityId);
        UUID secondLe = createLegalEntity(suffix + "-2");
        UUID approverRoleId = roleRepository.findByCode("GL_APPROVER").orElseThrow().getId();

        mockMvc.perform(post("/api/v1/auth/users/{id}/roles", userId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "roleId", approverRoleId.toString(),
                                "legalEntityId", secondLe.toString()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roleCode").value("GL_APPROVER"));
    }

    @Test
    void testRemoveRole_success() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID legalEntityId = createLegalEntity(suffix);
        UUID userId = createUserWithRole("removerole-" + suffix + "@evyoog.test", "GL_VIEWER", legalEntityId);
        UUID viewerRoleId = roleRepository.findByCode("GL_VIEWER").orElseThrow().getId();

        mockMvc.perform(delete("/api/v1/auth/users/{id}/roles/{roleId}", userId, viewerRoleId))
                .andExpect(status().isNoContent());
    }

    @Test
    void testDeactivateUser_asSysAdmin_success() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID legalEntityId = createLegalEntity(suffix);
        UUID userId = createUserWithRole("deactivate-" + suffix + "@evyoog.test", "GL_VIEWER", legalEntityId);

        mockMvc.perform(patch("/api/v1/auth/users/{id}/deactivate", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(false));
    }

    @Test
    void testResetPassword_returnsTemporaryPassword() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID legalEntityId = createLegalEntity(suffix);
        UUID userId = createUserWithRole("resetpwd-" + suffix + "@evyoog.test", "GL_VIEWER", legalEntityId);

        mockMvc.perform(post("/api/v1/auth/users/{id}/reset-password", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.temporaryPassword").isNotEmpty());
    }

    // ── role management ──────────────────────────────────────────────────────

    @Test
    void testCreateRole_success() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> request = Map.of(
                "code", "CUSTOM_ROLE_" + suffix,
                "name", "Custom Role",
                "description", "A custom role for testing",
                "permissionCodes", Set.of("gl:journal:view", "gl:trial-balance:view"));

        mockMvc.perform(post("/api/v1/auth/roles")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("CUSTOM_ROLE_" + suffix))
                .andExpect(jsonPath("$.data.isSystemRole").value(false))
                .andExpect(jsonPath("$.data.permissions.length()").value(2));
    }

    @Test
    void testListRoles_includesSeededSystemRoles() throws Exception {
        mockMvc.perform(get("/api/v1/auth/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code == 'SYS_ADMIN')]").exists())
                .andExpect(jsonPath("$.data[?(@.code == 'GL_MANAGER')]").exists());
    }

    @Test
    void testUpdateRole_customRole_success() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String createResponse = mockMvc.perform(post("/api/v1/auth/roles")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "EDITABLE_ROLE_" + suffix,
                                "name", "Editable Role",
                                "permissionCodes", Set.of("gl:journal:view")))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID roleId = UUID.fromString(objectMapper.readTree(createResponse).at("/data/id").asText());

        mockMvc.perform(patch("/api/v1/auth/roles/{id}", roleId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Renamed Role"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Renamed Role"));
    }

    @Test
    void testUpdateRole_seededSystemRole_returns409() throws Exception {
        UUID sysAdminRoleId = roleRepository.findByCode("SYS_ADMIN").orElseThrow().getId();

        mockMvc.perform(patch("/api/v1/auth/roles/{id}", sysAdminRoleId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Hacked Admin"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SYSTEM_ROLE_IMMUTABLE"));
    }

    @Test
    void testListPermissions_returnsSeededCatalog() throws Exception {
        mockMvc.perform(get("/api/v1/auth/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code == 'gl:journal:approve')]").exists());
    }

    // ── approval policy ───────────────────────────────────────────────────────

    @Test
    void testApprovalPolicy_crud_success() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID legalEntityId = createLegalEntity(suffix);

        Map<String, Object> createRequest = Map.of(
                "legalEntityId", legalEntityId.toString(),
                "journalSourceCode", "MANUAL",
                "requiresApproval", true,
                "approvalThresholdAmount", 50000,
                "approverRoleCode", "GL_APPROVER");

        String createResponse = mockMvc.perform(post("/api/v1/auth/approval-policies")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.requiresApproval").value(true))
                .andReturn().getResponse().getContentAsString();
        UUID policyId = UUID.fromString(objectMapper.readTree(createResponse).at("/data/id").asText());

        mockMvc.perform(get("/api/v1/auth/approval-policies").param("legalEntityId", legalEntityId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        Map<String, Object> updateRequest = Map.of(
                "legalEntityId", legalEntityId.toString(),
                "journalSourceCode", "MANUAL",
                "requiresApproval", false,
                "approverRoleCode", "GL_APPROVER");

        mockMvc.perform(put("/api/v1/auth/approval-policies/{id}", policyId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requiresApproval").value(false));

        mockMvc.perform(delete("/api/v1/auth/approval-policies/{id}", policyId))
                .andExpect(status().isNoContent());
    }

    @Test
    void testCreateApprovalPolicy_duplicateScope_returns409() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID legalEntityId = createLegalEntity(suffix);

        Map<String, Object> request = Map.of(
                "legalEntityId", legalEntityId.toString(),
                "journalSourceCode", "AP",
                "requiresApproval", true);

        mockMvc.perform(post("/api/v1/auth/approval-policies")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/approval-policies")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_APPROVAL_POLICY"));
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private UUID createLegalEntity(String suffix) throws Exception {
        UUID contextId = createConsumptionContext("CTX-" + suffix);
        UUID businessGroupId = createBusinessGroup(contextId, "BG-" + suffix);

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
                "name", "Auth Test Context");

        String response = mockMvc.perform(post("/api/v1/gl/consumption-contexts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).at("/data/id").asText());
    }

    private UUID createBusinessGroup(UUID contextId, String code) throws Exception {
        Map<String, Object> request = Map.of(
                "consumptionContextId", contextId.toString(),
                "code", code,
                "name", "Auth Test Business Group",
                "esMode", "THICK_ES",
                "defaultCurrency", "INR");

        String response = mockMvc.perform(post("/api/v1/gl/business-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).at("/data/id").asText());
    }

    /** Creates a user with the given role at the given Legal Entity, using the default superuser token. */
    private UUID createUserWithRole(String email, String roleCode, UUID legalEntityId) throws Exception {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();

        Map<String, Object> request = Map.of(
                "email", email,
                "fullName", "Test User " + roleCode,
                "password", TEST_PASSWORD,
                "roleId", role.getId().toString(),
                "legalEntityId", legalEntityId.toString());

        String response = mockMvc.perform(post("/api/v1/auth/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).at("/data/id").asText());
    }

    /** Logs in as the given user and returns the access token — sent as an explicit header,
     *  which overrides the default superuser token merged into every request by default. */
    private String loginAndGetAccessToken(String email, String password, UUID legalEntityId) throws Exception {
        Map<String, Object> request = Map.of(
                "email", email,
                "password", password,
                "legalEntityId", legalEntityId.toString());

        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).at("/data/accessToken").asText();
    }
}
