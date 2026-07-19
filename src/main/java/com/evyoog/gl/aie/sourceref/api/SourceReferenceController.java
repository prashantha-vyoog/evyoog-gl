package com.evyoog.gl.aie.sourceref.api;

import com.evyoog.gl.aie.sourceref.dto.CreateSourceReferenceRequest;
import com.evyoog.gl.aie.sourceref.dto.SourceReferenceResponse;
import com.evyoog.gl.aie.sourceref.service.SourceReferenceService;
import com.evyoog.gl.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "GL-19 AIE Source Reference")
public class SourceReferenceController {

    private final SourceReferenceService service;

    @PostMapping("/api/v1/aie/source-references")
    @PreAuthorize("hasAuthority('gl:aie:import')")
    @Operation(summary = "Link a journal to its originating source document")
    public ResponseEntity<ApiResponse<SourceReferenceResponse>> create(
            @Valid @RequestBody CreateSourceReferenceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.create(request)));
    }

    @GetMapping("/api/v1/aie/source-references/journal/{journalHeaderId}")
    @PreAuthorize("hasAuthority('gl:aie:view')")
    @Operation(summary = "All source references for a given journal")
    public ApiResponse<List<SourceReferenceResponse>> getByJournal(@PathVariable UUID journalHeaderId) {
        return ApiResponse.ok(service.getByJournal(journalHeaderId));
    }

    @GetMapping("/api/v1/aie/source-references/source/{sourceSystem}/{sourceDocumentId}")
    @PreAuthorize("hasAuthority('gl:aie:view')")
    @Operation(summary = "All journals linked to a given source document")
    public ApiResponse<List<SourceReferenceResponse>> getBySource(
            @PathVariable String sourceSystem, @PathVariable String sourceDocumentId) {
        return ApiResponse.ok(service.getBySource(sourceSystem, sourceDocumentId));
    }

    @GetMapping("/api/v1/aie/source-references/{id}")
    @PreAuthorize("hasAuthority('gl:aie:view')")
    @Operation(summary = "Get a single source reference by ID")
    public ApiResponse<SourceReferenceResponse> getById(@PathVariable UUID id) {
        return ApiResponse.ok(service.getById(id));
    }

    @DeleteMapping("/api/v1/aie/source-references/{id}")
    @PreAuthorize("hasAuthority('gl:aie:import')")
    @Operation(summary = "Delete a source reference — only allowed while the linked journal is DRAFT")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
