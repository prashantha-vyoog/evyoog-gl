package com.evyoog.gl.reporting.ledger.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Builder
public record JournalListingEntry(
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
        String status,
        String financeModeSnapshot,
        Instant postedAt,
        Instant createdAt
) {
}
