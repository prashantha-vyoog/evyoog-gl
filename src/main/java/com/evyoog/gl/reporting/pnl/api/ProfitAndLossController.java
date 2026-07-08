package com.evyoog.gl.reporting.pnl.api;

import com.evyoog.gl.common.response.ApiResponse;
import com.evyoog.gl.reporting.pnl.dto.ProfitAndLossResponse;
import com.evyoog.gl.reporting.pnl.service.ProfitAndLossService;
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
@Tag(name = "GL-23 Profit and Loss")
public class ProfitAndLossController {

    private final ProfitAndLossService profitAndLossService;

    @GetMapping("/api/v1/gl/reports/profit-and-loss")
    @PreAuthorize("hasAuthority('gl:pl:view')")
    @Operation(summary = "Generate the Profit and Loss statement for a Legal Entity and Period")
    public ApiResponse<ProfitAndLossResponse> getProfitAndLoss(
            @RequestParam UUID legalEntityId,
            @RequestParam UUID periodId) {
        return ApiResponse.ok(profitAndLossService.generate(legalEntityId, periodId));
    }

    @GetMapping("/api/v1/gl/reports/profit-and-loss/export")
    @PreAuthorize("hasAuthority('gl:pl:export')")
    @Operation(summary = "Export the Profit and Loss statement (Phase 1: JSON only — PDF/EXCEL generation is Phase 2)")
    public ApiResponse<ProfitAndLossResponse> exportProfitAndLoss(
            @RequestParam UUID legalEntityId,
            @RequestParam UUID periodId,
            @RequestParam(required = false, defaultValue = "PDF") String format) {
        return new ApiResponse<>(true, profitAndLossService.generate(legalEntityId, periodId),
                "Export format '" + format + "' is not yet implemented — returning structured JSON. "
                        + "PDF/Excel generation is planned for Phase 2.",
                null);
    }
}
