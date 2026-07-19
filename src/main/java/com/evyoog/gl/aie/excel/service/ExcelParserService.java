package com.evyoog.gl.aie.excel.service;

import com.evyoog.gl.aie.dto.AieImportRequest;
import com.evyoog.gl.aie.dto.AieLineRequest;
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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GL-17 Stage 1 adapter — parses an uploaded "Journal Lines" Excel sheet into
 * the same {@link AieImportRequest} shape GL-16's REST import accepts, so the
 * existing 4-stage AIE pipeline can be reused unchanged.
 */
@Service
public class ExcelParserService {

    private static final String SHEET_NAME = "Journal Lines";

    public AieImportRequest parse(MultipartFile file,
                                   UUID legalEntityId,
                                   UUID ledgerId,
                                   UUID accountingPeriodId,
                                   String createdBy,
                                   String sourceSystem) throws IOException {

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

            String eventId = null;
            String description = null;
            UUID resolvedLegalEntityId = legalEntityId;
            List<AieLineRequest> lines = new ArrayList<>();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isEmptyRow(row)) {
                    continue;
                }

                if (eventId == null) {
                    eventId = getCellString(row, colIndex, "eventid");
                    description = getCellString(row, colIndex, "description");
                    String fileLegalEntityId = getCellString(row, colIndex, "legalentityid");
                    if (fileLegalEntityId != null && !fileLegalEntityId.isBlank()) {
                        resolvedLegalEntityId = UUID.fromString(fileLegalEntityId);
                    }
                }

                lines.add(new AieLineRequest(
                        getCellInt(row, colIndex, "linenumber", lines.size() + 1),
                        getCellString(row, colIndex, "accountcode"),
                        Map.of(),
                        getCellBigDecimal(row, colIndex, "debitamount"),
                        getCellBigDecimal(row, colIndex, "creditamount"),
                        getCellString(row, colIndex, "linedescription"),
                        null,
                        null,
                        null,
                        null));
            }

            if (eventId == null || eventId.isBlank()) {
                eventId = "EXCEL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            }

            return new AieImportRequest(
                    eventId,
                    sourceSystem != null && !sourceSystem.isBlank() ? sourceSystem : "EXCEL_UPLOAD",
                    resolvedLegalEntityId,
                    ledgerId,
                    accountingPeriodId,
                    null,
                    description,
                    createdBy,
                    lines);
        }
    }

    public byte[] generateTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet(SHEET_NAME);
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

            Row example = sheet.createRow(1);
            example.createCell(0).setCellValue("EXCEL-001");
            example.createCell(1).setCellValue("EXCEL_UPLOAD");
            example.createCell(7).setCellValue(1);
            example.createCell(8).setCellValue("5100");
            example.createCell(9).setCellValue("Raw Material Cost");
            example.createCell(10).setCellValue(2200000);

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

    private Integer getCellInt(Row row, Map<String, Integer> colIndex, String colName, int defaultValue) {
        Cell cell = getCell(row, colIndex, colName);
        if (cell == null) {
            return defaultValue;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return (int) cell.getNumericCellValue();
        }
        if (cell.getCellType() == CellType.STRING) {
            String value = cell.getStringCellValue().trim();
            if (value.isBlank()) {
                return defaultValue;
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private BigDecimal getCellBigDecimal(Row row, Map<String, Integer> colIndex, String colName) {
        Cell cell = getCell(row, colIndex, colName);
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            return value == 0 ? null : BigDecimal.valueOf(value);
        }
        if (cell.getCellType() == CellType.STRING) {
            String value = cell.getStringCellValue().trim();
            if (value.isBlank()) {
                return null;
            }
            try {
                return new BigDecimal(value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
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
