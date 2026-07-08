package com.evyoog.gl.period.api;

import com.evyoog.gl.common.response.ApiResponse;
import com.evyoog.gl.period.dto.AccountingPeriodResponse;
import com.evyoog.gl.period.dto.GeneratePeriodsRequest;
import com.evyoog.gl.period.mapper.AccountingPeriodMapper;
import com.evyoog.gl.period.service.AccountingPeriodService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "GL-09 Period Management")
public class AccountingPeriodController {

    private final AccountingPeriodService service;
    private final AccountingPeriodMapper mapper;

    @PostMapping("/api/v1/gl/accounting-calendars/{calendarId}/periods/generate")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('gl:period:manage')")
    @Operation(summary = "Generate the periods for a specific fiscal year")
    public ApiResponse<List<AccountingPeriodResponse>> generate(
            @PathVariable UUID calendarId,
            @Valid @RequestBody GeneratePeriodsRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        List<AccountingPeriodResponse> periods = service.generatePeriodsForFiscalYear(calendarId, request.fiscalYear(), userId)
                .stream().map(mapper::toResponse).toList();
        return ApiResponse.created(periods);
    }

    @PostMapping("/api/v1/gl/accounting-calendars/{calendarId}/periods/generate-next")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('gl:period:manage')")
    @Operation(summary = "Generate periods for the fiscal year following the latest existing one")
    public ApiResponse<List<AccountingPeriodResponse>> generateNext(
            @PathVariable UUID calendarId,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        List<AccountingPeriodResponse> periods = service.generateNextFiscalYear(calendarId, userId)
                .stream().map(mapper::toResponse).toList();
        return ApiResponse.created(periods);
    }

    @GetMapping("/api/v1/gl/accounting-calendars/{calendarId}/periods")
    @PreAuthorize("hasAuthority('gl:period:view')")
    @Operation(summary = "List periods for a Calendar, ordered by start date, optionally filtered by fiscal year")
    public ApiResponse<List<AccountingPeriodResponse>> list(
            @PathVariable UUID calendarId,
            @RequestParam(required = false) String fiscalYear) {
        return ApiResponse.ok(service.listPeriods(calendarId, fiscalYear));
    }

    @GetMapping("/api/v1/gl/accounting-periods/{id}")
    @PreAuthorize("hasAuthority('gl:period:view')")
    @Operation(summary = "Get an accounting period by id")
    public ApiResponse<AccountingPeriodResponse> getById(@PathVariable UUID id) {
        return ApiResponse.ok(service.getById(id));
    }

    @GetMapping("/api/v1/gl/accounting-periods/find")
    @PreAuthorize("hasAuthority('gl:period:view')")
    @Operation(summary = "Find the period containing a given accounting date — used to resolve journal accounting dates")
    public ApiResponse<AccountingPeriodResponse> findByDate(
            @RequestParam UUID calendarId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(service.getByDate(calendarId, date));
    }
}
