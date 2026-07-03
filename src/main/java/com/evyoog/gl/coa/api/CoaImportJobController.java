package com.evyoog.gl.coa.api;

import com.evyoog.gl.coa.dto.CoaImportJobResponse;
import com.evyoog.gl.coa.service.CoaImportJobService;
import com.evyoog.gl.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * GET-only scaffold for GL-06, which will add the POST that creates and processes import jobs.
 */
@RestController
@RequestMapping("/api/v1/gl/coa-import-jobs")
@RequiredArgsConstructor
@Tag(name = "GL-05 CoA Import Jobs (scaffold)")
public class CoaImportJobController {

    private final CoaImportJobService service;

    @GetMapping("/{id}")
    @Operation(summary = "Get a CoA import job by id")
    public ApiResponse<CoaImportJobResponse> getById(@PathVariable UUID id) {
        return ApiResponse.ok(service.getById(id));
    }

    @GetMapping
    @Operation(summary = "List CoA import jobs for a Ledger")
    public ApiResponse<List<CoaImportJobResponse>> list(@RequestParam UUID ledgerId) {
        return ApiResponse.ok(service.list(ledgerId));
    }
}
