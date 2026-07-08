package com.evyoog.gl.journal.dto;

import com.evyoog.gl.posting.domain.JournalStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record JournalResponse(
        UUID id,
        String journalNumber,
        UUID legalEntityId,
        String legalEntityName,
        UUID ledgerId,
        String ledgerName,
        String financeMode,
        UUID accountingPeriodId,
        String periodName,
        String fiscalYear,
        UUID journalSourceId,
        String journalSourceCode,
        String journalSourceName,
        UUID journalCategoryId,
        String journalCategoryCode,
        String journalCategoryName,
        String description,
        LocalDate glDate,
        LocalDate accountingDate,
        String currencyCode,
        BigDecimal exchangeRate,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        JournalStatus status,
        String financeModeSnapshot,
        Instant postedAt,
        String postedBy,
        Boolean isReversal,
        String externalReference,
        String notes,
        Map<String, Object> extendedAttributes,
        List<JournalLineResponse> lines,
        Instant createdAt,
        String createdBy
) {
}
