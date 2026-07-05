package com.evyoog.gl.audit.api;

import com.evyoog.gl.audit.dto.AuditTrailPageResponse;
import com.evyoog.gl.audit.dto.AuditTrailResponse;
import com.evyoog.gl.audit.service.AuditTrailService;
import com.evyoog.gl.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "GL-29 Audit Trail")
public class AuditTrailController {

    private final AuditTrailService service;

    @GetMapping("/api/v1/gl/audit")
    @Operation(summary = "Search the audit trail — at least one filter is required to prevent a full-table scan")
    public ApiResponse<AuditTrailPageResponse> search(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) String performedBy,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.search(entityType, entityId, performedBy, action, from, to, page, size));
    }

    @GetMapping("/api/v1/gl/audit/entities/{entityType}/{entityId}")
    @Operation(summary = "Full paginated change history for a specific entity")
    public ApiResponse<AuditTrailPageResponse> getByEntity(
            @PathVariable String entityType,
            @PathVariable UUID entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.getByEntity(entityType, entityId, page, size));
    }

    @GetMapping("/api/v1/gl/audit/journals/{journalId}")
    @Operation(summary = "Full chronological lifecycle audit history for a journal — header changes, approval log, and reversal events")
    public ApiResponse<List<AuditTrailResponse>> getJournalAuditHistory(@PathVariable UUID journalId) {
        return ApiResponse.ok(service.getJournalAuditHistory(journalId));
    }

    @GetMapping("/api/v1/gl/audit/users/{performedBy}")
    @Operation(summary = "All actions performed by a specific user, optionally within a date range")
    public ApiResponse<AuditTrailPageResponse> getByUser(
            @PathVariable String performedBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.getByUser(performedBy, from, to, page, size));
    }
}
