package com.evyoog.gl.approval.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record JournalApprovalLogResponse(
        UUID id,
        UUID journalHeaderId,
        String journalNumber,
        String action,
        String performedBy,
        String comments,
        String fromStatus,
        String toStatus,
        Instant createdAt
) {
}
