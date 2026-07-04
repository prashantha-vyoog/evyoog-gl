package com.evyoog.gl.reporting.balancesheet.api;

import com.evyoog.gl.common.response.ApiResponse;
import com.evyoog.gl.reporting.balancesheet.dto.BalanceSheetResponse;
import com.evyoog.gl.reporting.balancesheet.service.BalanceSheetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "GL-24 Balance Sheet")
public class BalanceSheetController {

    private final BalanceSheetService balanceSheetService;

    @GetMapping("/api/v1/gl/reports/balance-sheet")
    @Operation(summary = "Generate the Balance Sheet for a Legal Entity and Period")
    public ApiResponse<BalanceSheetResponse> getBalanceSheet(
            @RequestParam UUID legalEntityId,
            @RequestParam UUID periodId) {
        return ApiResponse.ok(balanceSheetService.generate(legalEntityId, periodId));
    }

    @GetMapping("/api/v1/gl/reports/balance-sheet/export")
    @Operation(summary = "Export the Balance Sheet (Phase 1: JSON only — PDF/EXCEL generation is Phase 2)")
    public ApiResponse<BalanceSheetResponse> exportBalanceSheet(
            @RequestParam UUID legalEntityId,
            @RequestParam UUID periodId,
            @RequestParam(required = false, defaultValue = "PDF") String format) {
        return new ApiResponse<>(true, balanceSheetService.generate(legalEntityId, periodId),
                "Export format '" + format + "' is not yet implemented — returning structured JSON. "
                        + "PDF/Excel generation is planned for Phase 2.",
                null);
    }
}
