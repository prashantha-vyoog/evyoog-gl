package com.evyoog.gl.reporting.cashflow.api;

import com.evyoog.gl.common.response.ApiResponse;
import com.evyoog.gl.reporting.cashflow.dto.CashFlowResponse;
import com.evyoog.gl.reporting.cashflow.service.CashFlowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "GL-25 Cash Flow Statement")
public class CashFlowController {

    private final CashFlowService cashFlowService;

    @GetMapping("/api/v1/gl/reports/cash-flow")
    @PreAuthorize("hasAuthority('gl:balance-sheet:view')")
    @Operation(summary = "Generate the Cash Flow Statement (indirect method) for a Legal Entity and Period")
    public ApiResponse<CashFlowResponse> getCashFlow(
            @RequestParam UUID legalEntityId,
            @RequestParam UUID periodId) {
        return ApiResponse.ok(cashFlowService.getCashFlow(legalEntityId, periodId));
    }

    @GetMapping("/api/v1/gl/reports/cash-flow/export")
    @PreAuthorize("hasAuthority('gl:balance-sheet:export')")
    @Operation(summary = "Export the Cash Flow Statement (Phase 1: JSON only — PDF/EXCEL generation is Phase 2)")
    public ApiResponse<CashFlowResponse> exportCashFlow(
            @RequestParam UUID legalEntityId,
            @RequestParam UUID periodId,
            @RequestParam(required = false, defaultValue = "PDF") String format) {
        return new ApiResponse<>(true, cashFlowService.getCashFlow(legalEntityId, periodId),
                "Export format '" + format + "' is not yet implemented — returning structured JSON. "
                        + "PDF/Excel generation is planned for Phase 2.",
                null);
    }
}
