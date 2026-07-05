package com.evyoog.gl.approval.api;

import com.evyoog.gl.approval.dto.ApprovalActionRequest;
import com.evyoog.gl.approval.dto.ApprovalResponse;
import com.evyoog.gl.approval.dto.JournalApprovalLogResponse;
import com.evyoog.gl.approval.service.ApprovalService;
import com.evyoog.gl.common.response.ApiResponse;
import com.evyoog.gl.journal.dto.JournalSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "GL-12 Journal Approval")
public class ApprovalController {

    private final ApprovalService approvalService;

    @PostMapping("/api/v1/gl/journals/{id}/approve")
    @Operation(summary = "Approve a PENDING_APPROVAL journal — posts it immediately")
    public ApiResponse<ApprovalResponse> approve(
            @PathVariable UUID id,
            @Valid @RequestBody ApprovalActionRequest request) {
        return ApiResponse.ok(approvalService.approve(id, request));
    }

    @PostMapping("/api/v1/gl/journals/{id}/reject")
    @Operation(summary = "Reject a PENDING_APPROVAL journal — returns it to DRAFT")
    public ApiResponse<ApprovalResponse> reject(
            @PathVariable UUID id,
            @Valid @RequestBody ApprovalActionRequest request) {
        return ApiResponse.ok(approvalService.reject(id, request));
    }

    @PostMapping("/api/v1/gl/journals/{id}/recall")
    @Operation(summary = "Recall a PENDING_APPROVAL journal — returns it to DRAFT")
    public ApiResponse<ApprovalResponse> recall(
            @PathVariable UUID id,
            @Valid @RequestBody ApprovalActionRequest request) {
        return ApiResponse.ok(approvalService.recall(id, request));
    }

    @GetMapping("/api/v1/gl/journals/{id}/approval-history")
    @Operation(summary = "Full chronological approval audit trail for a journal")
    public ApiResponse<List<JournalApprovalLogResponse>> getApprovalHistory(@PathVariable UUID id) {
        return ApiResponse.ok(approvalService.getApprovalHistory(id));
    }

    @GetMapping("/api/v1/gl/approvals/pending")
    @Operation(summary = "Pending approval queue for a Legal Entity")
    public ApiResponse<List<JournalSummaryResponse>> getPendingApprovals(@RequestParam UUID legalEntityId) {
        return ApiResponse.ok(approvalService.getPendingApprovals(legalEntityId));
    }
}
