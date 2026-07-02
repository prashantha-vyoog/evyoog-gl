package com.evyoog.gl.ledger.dto;

import com.evyoog.gl.ledger.domain.LedgerCategory;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateLegalEntityLedgerRequest(

        @NotNull(message = "legalEntityId is required")
        UUID legalEntityId,

        @NotNull(message = "ledgerId is required")
        UUID ledgerId,

        LedgerCategory ledgerCategory
) {
}
