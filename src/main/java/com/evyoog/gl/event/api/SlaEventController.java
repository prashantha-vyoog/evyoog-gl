package com.evyoog.gl.event.api;

import com.evyoog.gl.common.response.ApiResponse;
import com.evyoog.gl.event.dto.SlaEventPageResponse;
import com.evyoog.gl.event.dto.SlaEventResponse;
import com.evyoog.gl.event.service.SlaEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "GL-20 Event Emission")
@PreAuthorize("hasAuthority('gl:audit:view')")
public class SlaEventController {

    private final SlaEventService service;

    @GetMapping("/api/v1/gl/events")
    @Operation(summary = "Search the SLA event log — at least one filter is required to prevent a full-table scan")
    public ApiResponse<SlaEventPageResponse> search(
            @RequestParam(required = false) UUID legalEntityId,
            @RequestParam(required = false) UUID ledgerId,
            @RequestParam(required = false) UUID accountingPeriodId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.search(legalEntityId, ledgerId, accountingPeriodId, status, from, to, page, size));
    }

    @GetMapping("/api/v1/gl/events/{id}")
    @Operation(summary = "Get a single SLA event by ID")
    public ApiResponse<SlaEventResponse> getById(@PathVariable UUID id) {
        return ApiResponse.ok(service.getById(id));
    }

    @GetMapping("/api/v1/gl/events/legal-entity/{legalEntityId}")
    @Operation(summary = "All SLA events for a specific Legal Entity, most recent first")
    public ApiResponse<SlaEventPageResponse> getByLegalEntity(
            @PathVariable UUID legalEntityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.getByLegalEntity(legalEntityId, page, size));
    }
}
