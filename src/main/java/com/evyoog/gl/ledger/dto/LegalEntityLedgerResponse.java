package com.evyoog.gl.ledger.dto;

import com.evyoog.gl.ledger.domain.FinanceMode;
import com.evyoog.gl.ledger.domain.LedgerCategory;

import java.time.Instant;
import java.util.UUID;

public record LegalEntityLedgerResponse(
        UUID id,
        UUID legalEntityId,
        String legalEntityName,
        UUID ledgerId,
        String ledgerName,
        String ledgerCode,
        FinanceMode financeMode,
        LedgerCategory ledgerCategory,
        boolean isActive,
        Instant createdAt
) {
}
