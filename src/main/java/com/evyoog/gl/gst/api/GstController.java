package com.evyoog.gl.gst.api;

import com.evyoog.gl.common.response.ApiResponse;
import com.evyoog.gl.gst.dto.CreateGstrExportRequest;
import com.evyoog.gl.gst.dto.GstTransactionDetail;
import com.evyoog.gl.gst.dto.Gstr1Response;
import com.evyoog.gl.gst.dto.GstrExportResponse;
import com.evyoog.gl.gst.dto.GstrSummaryResponse;
import com.evyoog.gl.gst.service.GstService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "GL-27 GST Flagging + GSTR Export")
public class GstController {

    private final GstService gstService;

    @GetMapping("/api/v1/gl/gst/gstr3b")
    @PreAuthorize("hasAuthority('gl:gst:view')")
    @Operation(summary = "GSTR-3B summary — output tax collected, input tax credit, and net payable")
    public ApiResponse<GstrSummaryResponse> getGstr3b(
            @RequestParam UUID legalEntityId,
            @RequestParam UUID periodId) {
        return ApiResponse.ok(gstService.generateGstr3b(legalEntityId, periodId));
    }

    @GetMapping("/api/v1/gl/gst/gstr1")
    @PreAuthorize("hasAuthority('gl:gst:view')")
    @Operation(summary = "GSTR-1 outward supplies detail")
    public ApiResponse<Gstr1Response> getGstr1(
            @RequestParam UUID legalEntityId,
            @RequestParam UUID periodId) {
        return ApiResponse.ok(gstService.generateGstr1(legalEntityId, periodId));
    }

    @GetMapping("/api/v1/gl/gst/transactions")
    @PreAuthorize("hasAuthority('gl:gst:view')")
    @Operation(summary = "All GST-applicable journal lines for a Legal Entity and period")
    public ApiResponse<List<GstTransactionDetail>> getTransactions(
            @RequestParam UUID legalEntityId,
            @RequestParam UUID periodId,
            @RequestParam(required = false) String gstType) {
        return ApiResponse.ok(gstService.listTransactions(legalEntityId, periodId, gstType));
    }

    @PostMapping("/api/v1/gl/gst/export")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('gl:gst:export')")
    @Operation(summary = "Generate and store a GSTR-1 or GSTR-3B export job")
    public ApiResponse<GstrExportResponse> createExport(
            @Valid @RequestBody CreateGstrExportRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        return ApiResponse.created(gstService.createExportJob(request, userId));
    }

    @GetMapping("/api/v1/gl/gst/export/{jobId}")
    @PreAuthorize("hasAuthority('gl:gst:view')")
    @Operation(summary = "Fetch a previously generated GSTR export job")
    public ApiResponse<GstrExportResponse> getExport(@PathVariable UUID jobId) {
        return ApiResponse.ok(gstService.getExportJob(jobId));
    }
}
