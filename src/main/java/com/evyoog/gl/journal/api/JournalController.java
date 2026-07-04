package com.evyoog.gl.journal.api;

import com.evyoog.gl.common.response.ApiResponse;
import com.evyoog.gl.journal.dto.CreateJournalRequest;
import com.evyoog.gl.journal.dto.JournalResponse;
import com.evyoog.gl.journal.dto.JournalSummaryResponse;
import com.evyoog.gl.journal.dto.UpdateJournalRequest;
import com.evyoog.gl.journal.service.JournalService;
import com.evyoog.gl.posting.domain.JournalStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "GL-11 Manual Journal Entry")
public class JournalController {

    private final JournalService service;

    @PostMapping("/api/v1/gl/journals")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a journal — posts immediately unless its source requires approval")
    public ApiResponse<JournalResponse> create(
            @Valid @RequestBody CreateJournalRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        return ApiResponse.created(service.create(request, userId));
    }

    @GetMapping("/api/v1/gl/journals/{id}")
    @Operation(summary = "Get a journal by id, with full line details")
    public ApiResponse<JournalResponse> getById(@PathVariable UUID id) {
        return ApiResponse.ok(service.findById(id));
    }

    @GetMapping("/api/v1/gl/journals")
    @Operation(summary = "List journals, filterable by legal entity, status, period, source, and gl date range")
    public ApiResponse<Page<JournalSummaryResponse>> list(
            @RequestParam UUID legalEntityId,
            @RequestParam(required = false) JournalStatus status,
            @RequestParam(required = false) UUID periodId,
            @RequestParam(required = false) String sourceCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Pageable pageable) {
        return ApiResponse.ok(service.list(legalEntityId, status, periodId, sourceCode, fromDate, toDate, pageable));
    }

    @PatchMapping("/api/v1/gl/journals/{id}")
    @Operation(summary = "Update description/notes on a DRAFT journal")
    public ApiResponse<JournalResponse> update(
            @PathVariable UUID id,
            @RequestBody UpdateJournalRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        return ApiResponse.ok(service.update(id, request, userId));
    }

    @PostMapping("/api/v1/gl/journals/{id}/submit")
    @Operation(summary = "Submit a DRAFT journal — posts directly, or moves to PENDING_APPROVAL if its source requires approval")
    public ApiResponse<JournalResponse> submit(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        return ApiResponse.ok(service.submit(id, userId));
    }

    @PostMapping("/api/v1/gl/journals/{id}/post")
    @Operation(summary = "Post an APPROVED journal")
    public ApiResponse<JournalResponse> post(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        return ApiResponse.ok(service.post(id, userId));
    }

    @PostMapping("/api/v1/gl/journals/{id}/cancel")
    @Operation(summary = "Cancel a DRAFT or PENDING_APPROVAL journal")
    public ApiResponse<JournalResponse> cancel(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        return ApiResponse.ok(service.cancel(id, userId));
    }
}
