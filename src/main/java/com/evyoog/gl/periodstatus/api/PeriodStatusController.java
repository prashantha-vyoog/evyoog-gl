package com.evyoog.gl.periodstatus.api;

import com.evyoog.gl.common.response.ApiResponse;
import com.evyoog.gl.periodstatus.domain.PeriodStatusEnum;
import com.evyoog.gl.periodstatus.dto.CreatePeriodStatusRequest;
import com.evyoog.gl.periodstatus.dto.PeriodStatusResponse;
import com.evyoog.gl.periodstatus.service.PeriodStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "GL-10 Period Status Control")
public class PeriodStatusController {

    private final PeriodStatusService service;

    @PostMapping("/api/v1/gl/period-status")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Initialise period status to NOT_OPENED for a Legal Entity and Period")
    public ApiResponse<PeriodStatusResponse> create(
            @Valid @RequestBody CreatePeriodStatusRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        return ApiResponse.created(service.create(request, userId));
    }

    @GetMapping("/api/v1/gl/period-status/{id}")
    @Operation(summary = "Get a period status row by id")
    public ApiResponse<PeriodStatusResponse> getById(@PathVariable UUID id) {
        return ApiResponse.ok(service.getById(id));
    }

    @GetMapping("/api/v1/gl/period-status")
    @Operation(summary = "List period status rows for a Legal Entity, optionally filtered by period or status")
    public ApiResponse<List<PeriodStatusResponse>> list(
            @RequestParam UUID legalEntityId,
            @RequestParam(required = false) UUID accountingPeriodId,
            @RequestParam(required = false) PeriodStatusEnum status) {
        return ApiResponse.ok(service.list(legalEntityId, accountingPeriodId, status));
    }

    @PostMapping("/api/v1/gl/period-status/{id}/open")
    @Operation(summary = "Transition a period to OPEN")
    public ApiResponse<PeriodStatusResponse> open(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        return ApiResponse.ok(service.open(id, userId));
    }

    @PostMapping("/api/v1/gl/period-status/{id}/future-enterable")
    @Operation(summary = "Transition a period to FUTURE_ENTERABLE — THICK mode only")
    public ApiResponse<PeriodStatusResponse> futureEnterable(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        return ApiResponse.ok(service.futureEnterable(id, userId));
    }

    @PostMapping("/api/v1/gl/period-status/{id}/close")
    @Operation(summary = "Transition a period to CLOSED")
    public ApiResponse<PeriodStatusResponse> close(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        return ApiResponse.ok(service.close(id, userId));
    }

    @PostMapping("/api/v1/gl/period-status/{id}/lock")
    @Operation(summary = "Transition a period to LOCKED — permanent, THICK mode only")
    public ApiResponse<PeriodStatusResponse> lock(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        return ApiResponse.ok(service.lock(id, userId));
    }
}
