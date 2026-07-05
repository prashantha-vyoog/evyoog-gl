package com.evyoog.gl.reversal.dto;

import com.evyoog.gl.posting.domain.JournalStatus;
import lombok.Builder;

import java.util.UUID;

@Builder
public record ReversalResponse(
        UUID originalJournalId,
        String originalJournalNumber,
        UUID reversalJournalId,
        String reversalJournalNumber,
        JournalStatus originalStatus,
        JournalStatus reversalStatus,
        String message
) {
}
