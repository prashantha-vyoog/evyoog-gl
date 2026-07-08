package com.evyoog.gl.coa.api;

import com.evyoog.gl.coa.dto.AccountResponse;
import com.evyoog.gl.coa.dto.AccountTreeResponse;
import com.evyoog.gl.coa.dto.CreateAccountRequest;
import com.evyoog.gl.coa.dto.UpdateAccountRequest;
import com.evyoog.gl.coa.service.ChartOfAccountsService;
import com.evyoog.gl.common.response.ApiResponse;
import com.evyoog.gl.dimension.domain.AccountQualifier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/gl/chart-of-accounts")
@RequiredArgsConstructor
@Tag(name = "GL-05 Chart of Accounts")
public class ChartOfAccountsController {

    private final ChartOfAccountsService service;

    @GetMapping
    @PreAuthorize("hasAuthority('gl:accounts:view')")
    @Operation(summary = "Get the Chart of Accounts hierarchy for a Ledger, optionally filtered by qualifier")
    public ApiResponse<AccountTreeResponse> getTree(
            @RequestParam UUID ledgerId,
            @RequestParam(required = false) AccountQualifier qualifier) {
        return ApiResponse.ok(service.getAccountTree(ledgerId, qualifier));
    }

    @GetMapping("/postable")
    @PreAuthorize("hasAuthority('gl:accounts:view')")
    @Operation(summary = "List postable accounts for a Ledger, excluding summary and date-expired accounts")
    public ApiResponse<List<AccountResponse>> getPostable(@RequestParam UUID ledgerId) {
        return ApiResponse.ok(service.getPostableAccounts(ledgerId));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('gl:accounts:view')")
    @Operation(summary = "Search accounts by code or name (case-insensitive)")
    public ApiResponse<List<AccountResponse>> search(
            @RequestParam UUID ledgerId,
            @RequestParam String query) {
        return ApiResponse.ok(service.search(ledgerId, query));
    }

    @GetMapping("/{accountId}")
    @PreAuthorize("hasAuthority('gl:accounts:view')")
    @Operation(summary = "Get a single account by id")
    public ApiResponse<AccountResponse> getById(@PathVariable UUID accountId) {
        return ApiResponse.ok(service.getById(accountId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('gl:accounts:create')")
    @Operation(summary = "Create an account in the Ledger's Natural Account hierarchy")
    public ApiResponse<AccountResponse> create(
            @Valid @RequestBody CreateAccountRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        return ApiResponse.created(service.createAccount(request, userId));
    }

    @PatchMapping("/{accountId}")
    @PreAuthorize("hasAuthority('gl:accounts:edit')")
    @Operation(summary = "Update mutable fields of an account")
    public ApiResponse<AccountResponse> update(
            @PathVariable UUID accountId,
            @Valid @RequestBody UpdateAccountRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        return ApiResponse.ok(service.updateAccount(accountId, request, userId));
    }

    @DeleteMapping("/{accountId}")
    @PreAuthorize("hasAuthority('gl:accounts:edit')")
    @Operation(summary = "Soft-delete an account (rejected if it has active children)")
    public ApiResponse<Void> deactivate(
            @PathVariable UUID accountId,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        service.deactivateAccount(accountId, userId);
        return ApiResponse.ok(null);
    }
}
