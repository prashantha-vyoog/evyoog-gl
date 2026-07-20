package com.evyoog.gl.coa.api;

import com.evyoog.gl.coa.dto.CoaImportJobResponse;
import com.evyoog.gl.coa.excel.service.CoaExcelParserService;
import com.evyoog.gl.coa.service.CoaImportJobService;
import com.evyoog.gl.common.exception.EvyoogException;
import com.evyoog.gl.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * GL-06 Chart of Accounts Excel Import. GL-05 scaffolded the GET-only job
 * lookup endpoints; this adds the POST that parses and processes an upload,
 * and the template download.
 */
@RestController
@RequestMapping("/api/v1/gl/coa-import-jobs")
@RequiredArgsConstructor
@Tag(name = "GL-06 CoA Excel Import")
public class CoaImportJobController {

    private final CoaImportJobService service;
    private final CoaExcelParserService excelParserService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('gl:accounts:create')")
    @Operation(summary = "Upload a Chart of Accounts Excel file and create accounts row by row")
    public ApiResponse<CoaImportJobResponse> importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("ledgerId") UUID ledgerId,
            @RequestParam(value = "createdBy", required = false) UUID createdBy) throws IOException {

        if (file.isEmpty()) {
            throw new EvyoogException("EMPTY_FILE", "Uploaded file is empty.", HttpStatus.BAD_REQUEST);
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !(filename.endsWith(".xlsx") || filename.endsWith(".xls"))) {
            throw new EvyoogException("INVALID_FILE_TYPE",
                    "Only Excel files (.xlsx, .xls) are accepted.", HttpStatus.BAD_REQUEST);
        }

        return ApiResponse.created(service.importFromExcel(file, ledgerId, createdBy));
    }

    @GetMapping("/template")
    @PreAuthorize("hasAuthority('gl:accounts:view')")
    @Operation(summary = "Download a blank Excel template for GL-06 Chart of Accounts import")
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        byte[] template = excelParserService.generateTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=chart_of_accounts_template.xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(template);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('gl:accounts:view')")
    @Operation(summary = "Get a CoA import job by id")
    public ApiResponse<CoaImportJobResponse> getById(@PathVariable UUID id) {
        return ApiResponse.ok(service.getById(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('gl:accounts:view')")
    @Operation(summary = "List CoA import jobs for a Ledger")
    public ApiResponse<List<CoaImportJobResponse>> list(@RequestParam UUID ledgerId) {
        return ApiResponse.ok(service.list(ledgerId));
    }
}
