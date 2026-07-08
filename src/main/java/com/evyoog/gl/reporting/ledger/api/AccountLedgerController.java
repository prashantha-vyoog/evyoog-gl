package com.evyoog.gl.reporting.ledger.api;

import com.evyoog.gl.common.response.ApiResponse;
import com.evyoog.gl.posting.domain.JournalStatus;
import com.evyoog.gl.reporting.ledger.dto.AccountLedgerResponse;
import com.evyoog.gl.reporting.ledger.dto.JournalListingEntry;
import com.evyoog.gl.reporting.ledger.service.AccountLedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "GL-26 Account Ledger + Journal Listing")
public class AccountLedgerController {

    private final AccountLedgerService accountLedgerService;

    @GetMapping("/api/v1/gl/reports/account-ledger")
    @PreAuthorize("hasAuthority('gl:account-ledger:view')")
    @Operation(summary = "Drill-down: all journal lines for one account in a period, with running balance")
    public ApiResponse<AccountLedgerResponse> getAccountLedger(
            @RequestParam UUID legalEntityId,
            @RequestParam UUID accountingPeriodId,
            @RequestParam UUID naturalAccountValueId) {
        return ApiResponse.ok(accountLedgerService.getAccountLedger(
                legalEntityId, accountingPeriodId, naturalAccountValueId));
    }

    @GetMapping("/api/v1/gl/reports/journal-listing")
    @PreAuthorize("hasAuthority('gl:account-ledger:view')")
    @Operation(summary = "List journals for a Legal Entity, filterable by status, period, source, and gl date range")
    public ApiResponse<Page<JournalListingEntry>> getJournalListing(
            @RequestParam UUID legalEntityId,
            @RequestParam(required = false) JournalStatus status,
            @RequestParam(required = false) UUID periodId,
            @RequestParam(required = false) String sourceCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Pageable pageable) {
        return ApiResponse.ok(accountLedgerService.getJournalListing(
                legalEntityId, status, periodId, sourceCode, fromDate, toDate, pageable));
    }
}
