package com.evyoog.gl.aie.api;

import com.evyoog.gl.aie.dto.AieImportRequest;
import com.evyoog.gl.aie.dto.AieImportResponse;
import com.evyoog.gl.aie.dto.AieLineErrorResponse;
import com.evyoog.gl.aie.dto.BatchStatusResponse;
import com.evyoog.gl.aie.dto.ResubmitRequest;
import com.evyoog.gl.aie.service.AiePipelineService;
import com.evyoog.gl.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "GL-16 AIE REST Import")
public class AieController {

    private final AiePipelineService service;

    @PostMapping("/api/v1/aie/journals")
    @Operation(summary = "Import and post a journal via the AIE REST pipeline (INGEST -> VALIDATE -> ENRICH -> POST)")
    public ResponseEntity<ApiResponse<AieImportResponse>> ingest(@Valid @RequestBody AieImportRequest request) {
        AieImportResponse response = service.ingest(request);
        HttpStatus status = "POSTED".equals(response.status()) ? HttpStatus.CREATED : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(ApiResponse.ok(response));
    }

    @GetMapping("/api/v1/aie/batches/{batchId}")
    @Operation(summary = "Get the status of an AIE import batch")
    public ApiResponse<BatchStatusResponse> getBatchStatus(@PathVariable UUID batchId) {
        return ApiResponse.ok(service.getBatchStatus(batchId));
    }

    @GetMapping("/api/v1/aie/batches/{batchId}/errors")
    @Operation(summary = "Get line-level validation/enrichment/posting errors for an AIE import batch")
    public ApiResponse<List<AieLineErrorResponse>> getErrors(@PathVariable UUID batchId) {
        return ApiResponse.ok(service.getErrors(batchId));
    }

    @PostMapping("/api/v1/aie/batches/{batchId}/resubmit")
    @Operation(summary = "Resubmit a FAILED AIE import batch under a new event_id")
    public ResponseEntity<ApiResponse<AieImportResponse>> resubmit(
            @PathVariable UUID batchId,
            @Valid @RequestBody ResubmitRequest request) {
        AieImportResponse response = service.resubmit(batchId, request);
        HttpStatus status = "POSTED".equals(response.status()) ? HttpStatus.CREATED : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(ApiResponse.ok(response));
    }
}
