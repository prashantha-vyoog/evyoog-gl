package com.evyoog.gl.approval.dto;

import com.evyoog.gl.posting.domain.JournalStatus;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record ApprovalResponse(
        UUID journalHeaderId,
        String journalNumber,
        JournalStatus newStatus,
        String message,
        List<JournalApprovalLogResponse> approvalHistory
) {
}
