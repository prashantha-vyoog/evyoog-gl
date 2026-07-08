package com.evyoog.gl.tds.api;

import com.evyoog.gl.common.response.ApiResponse;
import com.evyoog.gl.tds.dto.TdsDetailResponse;
import com.evyoog.gl.tds.dto.TdsReportResponse;
import com.evyoog.gl.tds.dto.TdsSectionInfo;
import com.evyoog.gl.tds.service.TdsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "GL-28 TDS/TCS Recording")
@PreAuthorize("hasAuthority('gl:tds:view')")
public class TdsController {

    private final TdsService tdsService;

    @GetMapping("/api/v1/gl/tds/report")
    @Operation(summary = "TDS summary grouped by section for a Legal Entity and period")
    public ApiResponse<TdsReportResponse> getTdsReport(
            @RequestParam UUID legalEntityId,
            @RequestParam UUID periodId) {
        return ApiResponse.ok(tdsService.generateTdsReport(legalEntityId, periodId));
    }

    @GetMapping("/api/v1/gl/tds/detail")
    @Operation(summary = "TDS transaction detail for a specific section")
    public ApiResponse<TdsDetailResponse> getTdsDetail(
            @RequestParam UUID legalEntityId,
            @RequestParam UUID periodId,
            @RequestParam String tdsSection) {
        return ApiResponse.ok(tdsService.getTdsDetail(legalEntityId, periodId, tdsSection));
    }

    @GetMapping("/api/v1/gl/tds/sections")
    @Operation(summary = "Known TDS sections with descriptions")
    public ApiResponse<List<TdsSectionInfo>> getSections() {
        return ApiResponse.ok(tdsService.listKnownSections());
    }
}
