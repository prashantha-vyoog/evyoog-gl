package com.evyoog.gl.ledger.api;

import com.evyoog.gl.common.response.ApiResponse;
import com.evyoog.gl.ledger.dto.CreateLegalEntityLedgerRequest;
import com.evyoog.gl.ledger.dto.LegalEntityLedgerResponse;
import com.evyoog.gl.ledger.service.LegalEntityLedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api/v1/gl/legal-entity-ledgers")
@RequiredArgsConstructor
@Tag(name = "GL-03 Legal Entity Ledger Assignments")
public class LegalEntityLedgerController {

    private final LegalEntityLedgerService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Assign a ledger to a legal entity (enforces one-PRIMARY and Thin ES mode rules)")
    public ApiResponse<LegalEntityLedgerResponse> create(
            @Valid @RequestBody CreateLegalEntityLedgerRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        return ApiResponse.created(service.create(request, userId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a legal entity - ledger assignment by id")
    public ApiResponse<LegalEntityLedgerResponse> getById(@PathVariable UUID id) {
        return ApiResponse.ok(service.getById(id));
    }

    @GetMapping
    @Operation(summary = "List legal entity - ledger assignments, filtered by legal entity or ledger")
    public ApiResponse<List<LegalEntityLedgerResponse>> list(
            @RequestParam(required = false) UUID legalEntityId,
            @RequestParam(required = false) UUID ledgerId) {
        return ApiResponse.ok(service.list(legalEntityId, ledgerId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete a legal entity - ledger assignment (isActive = false)")
    public ApiResponse<Void> deactivate(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        service.deactivate(id, userId);
        return ApiResponse.ok(null);
    }
}
