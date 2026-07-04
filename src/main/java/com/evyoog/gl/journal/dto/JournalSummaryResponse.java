package com.evyoog.gl.journal.dto;

import com.evyoog.gl.posting.domain.JournalStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record JournalSummaryResponse(
        UUID id,
        String journalNumber,
        String legalEntityName,
        String ledgerName,
        String periodName,
        String journalSourceCode,
        String journalCategoryCode,
        String description,
        LocalDate glDate,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        JournalStatus status,
        String financeModeSnapshot,
        Instant postedAt,
        Instant createdAt
) {
}
