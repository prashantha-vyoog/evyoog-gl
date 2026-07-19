package com.evyoog.gl.aie.excel.service;

import com.evyoog.gl.aie.dto.AieImportRequest;
import com.evyoog.gl.aie.dto.AieLineRequest;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelParserServiceTest {

    private final ExcelParserService service = new ExcelParserService();

    @Test
    void testParse_validExcelFile_returnsCorrectRequest() throws Exception {
        UUID legalEntityId = UUID.randomUUID();
        UUID ledgerId = UUID.randomUUID();
        UUID periodId = UUID.randomUUID();

        MockMultipartFile file = workbook(headerAndOneLine("EXC-2025-APR-001", "April 2025 Expense Journal"));

        AieImportRequest request = service.parse(file, legalEntityId, ledgerId, periodId,
                "accountant@orbinox.com", "EXCEL_UPLOAD");

        assertThat(request.eventId()).isEqualTo("EXC-2025-APR-001");
        assertThat(request.sourceSystem()).isEqualTo("EXCEL_UPLOAD");
        assertThat(request.legalEntityId()).isEqualTo(legalEntityId);
        assertThat(request.ledgerId()).isEqualTo(ledgerId);
        assertThat(request.accountingPeriodId()).isEqualTo(periodId);
        assertThat(request.description()).isEqualTo("April 2025 Expense Journal");
        assertThat(request.createdBy()).isEqualTo("accountant@orbinox.com");
        assertThat(request.lines()).hasSize(1);

        AieLineRequest line = request.lines().get(0);
        assertThat(line.lineNumber()).isEqualTo(1);
        assertThat(line.accountCode()).isEqualTo("5100");
        assertThat(line.description()).isEqualTo("Raw Material Cost");
        assertThat(line.debitAmount()).isEqualByComparingTo(new BigDecimal("2200000"));
        assertThat(line.creditAmount()).isNull();
    }

    @Test
    void testParse_multipleLines_allParsedCorrectly() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Journal Lines");
        writeHeader(sheet);

        Row row1 = sheet.createRow(1);
        row1.createCell(0).setCellValue("EXC-002");
        row1.createCell(7).setCellValue(1);
        row1.createCell(8).setCellValue("5100");
        row1.createCell(9).setCellValue("Raw Material Cost");
        row1.createCell(10).setCellValue(2200000);

        Row row2 = sheet.createRow(2);
        row2.createCell(7).setCellValue(2);
        row2.createCell(8).setCellValue("2100");
        row2.createCell(9).setCellValue("Accounts Payable");
        row2.createCell(11).setCellValue(2200000);

        MockMultipartFile file = workbook(workbook);

        AieImportRequest request = service.parse(file, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "accountant@orbinox.com", "EXCEL_UPLOAD");

        assertThat(request.lines()).hasSize(2);
        assertThat(request.lines().get(0).accountCode()).isEqualTo("5100");
        assertThat(request.lines().get(1).accountCode()).isEqualTo("2100");
        assertThat(request.lines().get(1).creditAmount()).isEqualByComparingTo(new BigDecimal("2200000"));
    }

    @Test
    void testParse_missingEventId_generatesOne() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Journal Lines");
        writeHeader(sheet);

        Row row1 = sheet.createRow(1);
        row1.createCell(7).setCellValue(1);
        row1.createCell(8).setCellValue("5100");
        row1.createCell(10).setCellValue(1000);

        MockMultipartFile file = workbook(workbook);

        AieImportRequest request = service.parse(file, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "accountant@orbinox.com", "EXCEL_UPLOAD");

        assertThat(request.eventId()).startsWith("EXCEL-");
    }

    @Test
    void testParse_emptyRows_skipped() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Journal Lines");
        writeHeader(sheet);

        sheet.createRow(1); // blank row — no cells

        Row row2 = sheet.createRow(2);
        row2.createCell(0).setCellValue("EXC-003");
        row2.createCell(7).setCellValue(1);
        row2.createCell(8).setCellValue("5100");
        row2.createCell(10).setCellValue(1000);

        MockMultipartFile file = workbook(workbook);

        AieImportRequest request = service.parse(file, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "accountant@orbinox.com", "EXCEL_UPLOAD");

        assertThat(request.lines()).hasSize(1);
        assertThat(request.eventId()).isEqualTo("EXC-003");
    }

    @Test
    void testParse_debitAndCredit_parsedAsBigDecimal() throws Exception {
        MockMultipartFile file = workbook(headerAndOneLine("EXC-004", "desc"));

        AieImportRequest request = service.parse(file, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "accountant@orbinox.com", "EXCEL_UPLOAD");

        AieLineRequest line = request.lines().get(0);
        assertThat(line.debitAmount()).isInstanceOf(BigDecimal.class);
        assertThat(line.debitAmount()).isEqualByComparingTo(new BigDecimal("2200000"));
    }

    @Test
    void testParse_blankAmounts_returnedAsNull() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Journal Lines");
        writeHeader(sheet);

        Row row1 = sheet.createRow(1);
        row1.createCell(0).setCellValue("EXC-005");
        row1.createCell(7).setCellValue(1);
        row1.createCell(8).setCellValue("2100");
        // debitAmount and creditAmount left blank

        MockMultipartFile file = workbook(workbook);

        AieImportRequest request = service.parse(file, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "accountant@orbinox.com", "EXCEL_UPLOAD");

        AieLineRequest line = request.lines().get(0);
        assertThat(line.debitAmount()).isNull();
        assertThat(line.creditAmount()).isNull();
    }

    @Test
    void testGenerateTemplate_returnsValidXlsx() throws Exception {
        byte[] template = service.generateTemplate();

        assertThat(template).isNotEmpty();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(template))) {
            Sheet sheet = workbook.getSheet("Journal Lines");
            assertThat(sheet).isNotNull();
            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("eventId");
            assertThat(header.getCell(11).getStringCellValue()).isEqualTo("creditAmount");
        }
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private void writeHeader(Sheet sheet) {
        Row header = sheet.createRow(0);
        String[] columns = {
                "eventId", "sourceSystem", "legalEntityId", "ledgerId",
                "accountingPeriodId", "description", "createdBy",
                "lineNumber", "accountCode", "lineDescription",
                "debitAmount", "creditAmount"
        };
        for (int i = 0; i < columns.length; i++) {
            header.createCell(i).setCellValue(columns[i]);
        }
    }

    private XSSFWorkbook headerAndOneLine(String eventId, String description) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Journal Lines");
        writeHeader(sheet);

        Row row = sheet.createRow(1);
        row.createCell(0).setCellValue(eventId);
        row.createCell(1).setCellValue("EXCEL_UPLOAD");
        row.createCell(5).setCellValue(description);
        row.createCell(6).setCellValue("accountant@orbinox.com");
        row.createCell(7).setCellValue(1);
        row.createCell(8).setCellValue("5100");
        row.createCell(9).setCellValue("Raw Material Cost");
        row.createCell(10).setCellValue(2200000);
        return workbook;
    }

    private MockMultipartFile workbook(XSSFWorkbook workbook) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            workbook.close();
            return new MockMultipartFile("file", "journal_import.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }
}
