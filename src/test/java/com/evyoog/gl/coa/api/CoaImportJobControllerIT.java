package com.evyoog.gl.coa.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class CoaImportJobControllerIT {

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
    void testImport_validExcel_returnsCompleted() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID ledgerId = createLedger("LDG-" + suffix);
        createDimension(ledgerId, "NA-" + suffix);

        MockMultipartFile file = accountsFile(
                row("1000-" + suffix, "Assets", "ASSET", null, true, false, null, null),
                row("1100-" + suffix, "Cash", "ASSET", "1000-" + suffix, true, false, "DR", "Cash on hand"));

        mockMvc.perform(multipart("/api/v1/gl/coa-import-jobs")
                        .file(file)
                        .param("ledgerId", ledgerId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.totalRows").value(2))
                .andExpect(jsonPath("$.data.successRows").value(2))
                .andExpect(jsonPath("$.data.errorRows").value(0));
    }

    @Test
    void testImport_partialErrors_returnsCompletedWithErrors() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID ledgerId = createLedger("LDG-" + suffix);
        createDimension(ledgerId, "NA-" + suffix);

        MockMultipartFile file = accountsFile(
                row("1000-" + suffix, "Assets", "ASSET", null, true, false, null, null),
                row("2000-" + suffix, "Liabilities", "NOT_REAL", null, true, false, null, null));

        mockMvc.perform(multipart("/api/v1/gl/coa-import-jobs")
                        .file(file)
                        .param("ledgerId", ledgerId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("COMPLETED_WITH_ERRORS"))
                .andExpect(jsonPath("$.data.successRows").value(1))
                .andExpect(jsonPath("$.data.errorRows").value(1))
                .andExpect(jsonPath("$.data.errorDetails.errors[0].errorCode").value("INVALID_QUALIFIER"));
    }

    @Test
    void testImport_allInvalid_returnsFailed() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID ledgerId = createLedger("LDG-" + suffix);
        createDimension(ledgerId, "NA-" + suffix);

        MockMultipartFile file = accountsFile(
                row(null, "No Code", "ASSET", null, true, false, null, null));

        mockMvc.perform(multipart("/api/v1/gl/coa-import-jobs")
                        .file(file)
                        .param("ledgerId", ledgerId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.successRows").value(0));
    }

    @Test
    void testImport_duplicateCode_recordsErrorNotException() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID ledgerId = createLedger("LDG-" + suffix);
        createDimension(ledgerId, "NA-" + suffix);

        MockMultipartFile file = accountsFile(
                row("1000-" + suffix, "Assets", "ASSET", null, true, false, null, null),
                row("1000-" + suffix, "Assets Dup", "ASSET", null, true, false, null, null));

        mockMvc.perform(multipart("/api/v1/gl/coa-import-jobs")
                        .file(file)
                        .param("ledgerId", ledgerId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("COMPLETED_WITH_ERRORS"))
                .andExpect(jsonPath("$.data.successRows").value(1))
                .andExpect(jsonPath("$.data.errorRows").value(1))
                .andExpect(jsonPath("$.data.errorDetails.errors[0].errorCode").value("DUPLICATE_DIMENSION_VALUE_CODE"));
    }

    @Test
    void testImport_emptyFile_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "coa_import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);

        mockMvc.perform(multipart("/api/v1/gl/coa-import-jobs")
                        .file(file)
                        .param("ledgerId", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPTY_FILE"));
    }

    @Test
    void testImport_invalidFileType_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "coa_import.txt",
                MediaType.TEXT_PLAIN_VALUE, "not an excel file".getBytes());

        mockMvc.perform(multipart("/api/v1/gl/coa-import-jobs")
                        .file(file)
                        .param("ledgerId", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE_TYPE"));
    }

    @Test
    void testImport_ledgerNotFound_returns404() throws Exception {
        MockMultipartFile file = accountsFile(row("1000", "Assets", "ASSET", null, true, false, null, null));

        mockMvc.perform(multipart("/api/v1/gl/coa-import-jobs")
                        .file(file)
                        .param("ledgerId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void testGetJob_returnsJobDetails() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID ledgerId = createLedger("LDG-" + suffix);
        createDimension(ledgerId, "NA-" + suffix);

        MockMultipartFile file = accountsFile(row("1000-" + suffix, "Assets", "ASSET", null, true, false, null, null));

        String createResponse = mockMvc.perform(multipart("/api/v1/gl/coa-import-jobs")
                        .file(file)
                        .param("ledgerId", ledgerId.toString()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String jobId = objectMapper.readTree(createResponse).at("/data/id").asText();

        mockMvc.perform(get("/api/v1/gl/coa-import-jobs/{id}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.fileName").value("coa_import.xlsx"));
    }

    @Test
    void testGetJob_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/gl/coa-import-jobs/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void testDownloadTemplate_returnsXlsx() throws Exception {
        mockMvc.perform(get("/api/v1/gl/coa-import-jobs/template"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        containsString("chart_of_accounts_template.xlsx")));
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private record Row8(String code, String name, String qualifier, String parentCode,
                         Boolean isPostable, Boolean isSummary, String normalBalance, String description) {
    }

    private Row8 row(String code, String name, String qualifier, String parentCode,
                      Boolean isPostable, Boolean isSummary, String normalBalance, String description) {
        return new Row8(code, name, qualifier, parentCode, isPostable, isSummary, normalBalance, description);
    }

    private MockMultipartFile accountsFile(Row8... dataRows) throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Accounts");
        Row header = sheet.createRow(0);
        String[] columns = {"code", "name", "qualifier", "parentCode", "isPostable", "isSummary", "normalBalance", "description"};
        for (int i = 0; i < columns.length; i++) {
            header.createCell(i).setCellValue(columns[i]);
        }

        int rowNum = 1;
        for (Row8 data : dataRows) {
            Row row = sheet.createRow(rowNum++);
            if (data.code() != null) row.createCell(0).setCellValue(data.code());
            if (data.name() != null) row.createCell(1).setCellValue(data.name());
            if (data.qualifier() != null) row.createCell(2).setCellValue(data.qualifier());
            if (data.parentCode() != null) row.createCell(3).setCellValue(data.parentCode());
            if (data.isPostable() != null) row.createCell(4).setCellValue(data.isPostable());
            if (data.isSummary() != null) row.createCell(5).setCellValue(data.isSummary());
            if (data.normalBalance() != null) row.createCell(6).setCellValue(data.normalBalance());
            if (data.description() != null) row.createCell(7).setCellValue(data.description());
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            workbook.close();
            return new MockMultipartFile("file", "coa_import.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    private UUID createDimension(UUID ledgerId, String code) throws Exception {
        Map<String, Object> request = Map.of(
                "ledgerId", ledgerId.toString(),
                "code", code,
                "name", "Natural Account " + code,
                "dimensionType", "NATURAL_ACCOUNT");

        String response = mockMvc.perform(post("/api/v1/gl/finance-dimensions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).at("/data/id").asText());
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
