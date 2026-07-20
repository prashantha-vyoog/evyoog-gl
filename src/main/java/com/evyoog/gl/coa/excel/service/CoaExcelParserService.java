package com.evyoog.gl.coa.excel.service;

import com.evyoog.gl.coa.excel.dto.ParsedAccountRow;
import com.evyoog.gl.common.exception.EvyoogException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GL-06 — parses an uploaded "Accounts" Excel sheet into {@link ParsedAccountRow}
 * values. Follows the same WorkbookFactory / column-normalisation pattern as
 * GL-17's {@code ExcelParserService}. Row-level business validation (required
 * fields, qualifier legality, parentCode resolution) happens downstream in
 * {@code CoaImportJobService}, not here.
 */
@Service
public class CoaExcelParserService {

    private static final String SHEET_NAME = "Accounts";

    public List<ParsedAccountRow> parse(MultipartFile file) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null) {
                sheet = workbook.getSheetAt(0);
            }

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new EvyoogException("EMPTY_FILE", "Excel file has no header row.", HttpStatus.BAD_REQUEST);
            }
            Map<String, Integer> colIndex = buildColumnIndex(headerRow);

            List<ParsedAccountRow> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isEmptyRow(row)) {
                    continue;
                }

                rows.add(new ParsedAccountRow(
                        i + 1,
                        getCellString(row, colIndex, "code"),
                        getCellString(row, colIndex, "name"),
                        getCellString(row, colIndex, "qualifier"),
                        getCellString(row, colIndex, "parentcode"),
                        getCellBoolean(row, colIndex, "ispostable"),
                        getCellBoolean(row, colIndex, "issummary"),
                        getCellString(row, colIndex, "normalbalance"),
                        getCellString(row, colIndex, "description")));
            }
            return rows;
        }
    }

    public byte[] generateTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet(SHEET_NAME);
            Row header = sheet.createRow(0);
            String[] columns = {
                    "code", "name", "qualifier", "parentCode",
                    "isPostable", "isSummary", "normalBalance", "description"
            };
            for (int i = 0; i < columns.length; i++) {
                header.createCell(i).setCellValue(columns[i]);
            }

            Row example = sheet.createRow(1);
            example.createCell(0).setCellValue("5800");
            example.createCell(1).setCellValue("Research and Development");
            example.createCell(2).setCellValue("EXPENSE");
            example.createCell(4).setCellValue(true);
            example.createCell(5).setCellValue(false);
            example.createCell(6).setCellValue("DR");
            example.createCell(7).setCellValue("R&D expenses");

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private Map<String, Integer> buildColumnIndex(Row headerRow) {
        Map<String, Integer> index = new HashMap<>();
        for (Cell cell : headerRow) {
            String header = normalise(cell.getStringCellValue());
            if (!header.isBlank()) {
                index.put(header, cell.getColumnIndex());
            }
        }
        return index;
    }

    private String normalise(String header) {
        return header.trim().toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private String getCellString(Row row, Map<String, Integer> colIndex, String colName) {
        Cell cell = getCell(row, colIndex, colName);
        if (cell == null) {
            return null;
        }
        return switch (cell.getCellType()) {
            case STRING -> {
                String value = cell.getStringCellValue().trim();
                yield value.isBlank() ? null : value;
            }
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default -> null;
        };
    }

    private Boolean getCellBoolean(Row row, Map<String, Integer> colIndex, String colName) {
        Cell cell = getCell(row, colIndex, colName);
        if (cell == null) {
            return null;
        }
        return switch (cell.getCellType()) {
            case BOOLEAN -> cell.getBooleanCellValue();
            case STRING -> {
                String value = cell.getStringCellValue().trim();
                yield value.isBlank() ? null : value.equalsIgnoreCase("TRUE") || value.equals("1");
            }
            case NUMERIC -> cell.getNumericCellValue() != 0;
            default -> null;
        };
    }

    private Cell getCell(Row row, Map<String, Integer> colIndex, String colName) {
        Integer idx = colIndex.get(colName);
        return idx == null ? null : row.getCell(idx);
    }

    private boolean isEmptyRow(Row row) {
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }
}
