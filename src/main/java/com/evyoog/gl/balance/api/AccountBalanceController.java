package com.evyoog.gl.balance.api;

import com.evyoog.gl.balance.dto.AccountBalanceResponse;
import com.evyoog.gl.balance.dto.AccountBalanceSummaryResponse;
import com.evyoog.gl.balance.dto.CarryForwardRequest;
import com.evyoog.gl.balance.dto.CarryForwardResponse;
import com.evyoog.gl.balance.service.AccountBalanceService;
import com.evyoog.gl.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "GL-21 Account Balance Maintenance")
public class AccountBalanceController {

    private final AccountBalanceService service;

    @GetMapping("/api/v1/gl/account-balances")
    @PreAuthorize("hasAuthority('gl:balance:view')")
    @Operation(summary = "List account balances for a Legal Entity and Period, optionally filtered by account code")
    public ApiResponse<List<AccountBalanceResponse>> getBalances(
            @RequestParam UUID legalEntityId,
            @RequestParam UUID periodId,
            @RequestParam(required = false) String accountCode) {
        return ApiResponse.ok(service.getBalances(legalEntityId, periodId, accountCode));
    }

    @GetMapping("/api/v1/gl/account-balances/summary")
    @PreAuthorize("hasAuthority('gl:balance:view')")
    @Operation(summary = "Get balance totals by account qualifier for a Legal Entity and Period")
    public ApiResponse<AccountBalanceSummaryResponse> getSummary(
            @RequestParam UUID legalEntityId,
            @RequestParam UUID periodId) {
        return ApiResponse.ok(service.getSummary(legalEntityId, periodId));
    }

    @PostMapping("/api/v1/gl/account-balances/carry-forward")
    @PreAuthorize("hasAuthority('gl:balance:manage')")
    @Operation(summary = "Carry forward Balance Sheet ending balances as beginning balances in the next period")
    public ApiResponse<CarryForwardResponse> carryForward(
            @Valid @RequestBody CarryForwardRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        return ApiResponse.ok(service.carryForward(
                request.legalEntityId(), request.fromPeriodId(), request.toPeriodId(), userId));
    }
}
