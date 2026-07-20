package com.evyoog.gl.coa.excel.service;

import com.evyoog.gl.coa.excel.dto.ParsedAccountRow;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CoaExcelParserServiceTest {

    private final CoaExcelParserService service = new CoaExcelParserService();

    @Test
    void testParse_validFile_returnsAllRows() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Accounts");
        writeHeader(sheet);

        Row row1 = sheet.createRow(1);
        row1.createCell(0).setCellValue("5800");
        row1.createCell(1).setCellValue("Research and Development");
        row1.createCell(2).setCellValue("EXPENSE");
        row1.createCell(4).setCellValue(true);
        row1.createCell(5).setCellValue(false);
        row1.createCell(6).setCellValue("DR");
        row1.createCell(7).setCellValue("R&D expenses");

        Row row2 = sheet.createRow(2);
        row2.createCell(0).setCellValue("5810");
        row2.createCell(1).setCellValue("Prototyping");
        row2.createCell(2).setCellValue("EXPENSE");
        row2.createCell(3).setCellValue("5800");

        List<ParsedAccountRow> rows = service.parse(workbook(workbook));

        assertThat(rows).hasSize(2);
        ParsedAccountRow first = rows.get(0);
        assertThat(first.rowNumber()).isEqualTo(2);
        assertThat(first.code()).isEqualTo("5800");
        assertThat(first.name()).isEqualTo("Research and Development");
        assertThat(first.qualifier()).isEqualTo("EXPENSE");
        assertThat(first.isPostable()).isTrue();
        assertThat(first.isSummary()).isFalse();
        assertThat(first.normalBalance()).isEqualTo("DR");
        assertThat(first.description()).isEqualTo("R&D expenses");

        ParsedAccountRow second = rows.get(1);
        assertThat(second.parentCode()).isEqualTo("5800");
        assertThat(second.isPostable()).isNull();
        assertThat(second.isSummary()).isNull();
        assertThat(second.normalBalance()).isNull();
    }

    @Test
    void testParse_missingCode_returnsNullCode() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Accounts");
        writeHeader(sheet);

        Row row1 = sheet.createRow(1);
        row1.createCell(1).setCellValue("No Code Account");
        row1.createCell(2).setCellValue("ASSET");

        List<ParsedAccountRow> rows = service.parse(workbook(workbook));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).code()).isNull();
        assertThat(rows.get(0).rowNumber()).isEqualTo(2);
    }

    @Test
    void testParse_blankRows_skipped() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Accounts");
        writeHeader(sheet);

        sheet.createRow(1); // blank row — no cells

        Row row2 = sheet.createRow(2);
        row2.createCell(0).setCellValue("1000");
        row2.createCell(1).setCellValue("Assets");
        row2.createCell(2).setCellValue("ASSET");

        List<ParsedAccountRow> rows = service.parse(workbook(workbook));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).code()).isEqualTo("1000");
        assertThat(rows.get(0).rowNumber()).isEqualTo(3);
    }

    @Test
    void testParse_booleanFields_parsedFromStringAndNumeric() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Accounts");
        writeHeader(sheet);

        Row row1 = sheet.createRow(1);
        row1.createCell(0).setCellValue("1000");
        row1.createCell(1).setCellValue("Assets");
        row1.createCell(2).setCellValue("ASSET");
        row1.createCell(4).setCellValue("TRUE");
        row1.createCell(5).setCellValue("FALSE");

        List<ParsedAccountRow> rows = service.parse(workbook(workbook));

        assertThat(rows.get(0).isPostable()).isTrue();
        assertThat(rows.get(0).isSummary()).isFalse();
    }

    @Test
    void testParse_firstSheetUsed_whenNoAccountsSheet() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Sheet1");
        writeHeader(sheet);

        Row row1 = sheet.createRow(1);
        row1.createCell(0).setCellValue("1000");
        row1.createCell(1).setCellValue("Assets");
        row1.createCell(2).setCellValue("ASSET");

        List<ParsedAccountRow> rows = service.parse(workbook(workbook));

        assertThat(rows).hasSize(1);
    }

    @Test
    void testGenerateTemplate_returnsValidXlsx() throws Exception {
        byte[] template = service.generateTemplate();

        assertThat(template).isNotEmpty();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(template))) {
            Sheet sheet = workbook.getSheet("Accounts");
            assertThat(sheet).isNotNull();
            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("code");
            assertThat(header.getCell(7).getStringCellValue()).isEqualTo("description");
        }
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private void writeHeader(Sheet sheet) {
        Row header = sheet.createRow(0);
        String[] columns = {
                "code", "name", "qualifier", "parentCode",
                "isPostable", "isSummary", "normalBalance", "description"
        };
        for (int i = 0; i < columns.length; i++) {
            header.createCell(i).setCellValue(columns[i]);
        }
    }

    private MockMultipartFile workbook(XSSFWorkbook workbook) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            workbook.close();
            return new MockMultipartFile("file", "coa_import.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }
}
