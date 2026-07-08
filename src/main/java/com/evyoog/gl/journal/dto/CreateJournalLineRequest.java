package com.evyoog.gl.journal.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record CreateJournalLineRequest(
        @NotNull(message = "accountCombination is required")
        Map<String, String> accountCombination,

        @NotNull(message = "naturalAccountValueId is required")
        UUID naturalAccountValueId,

        BigDecimal debitAmount,

        BigDecimal creditAmount,

        String currencyCode,

        String description,

        Boolean gstApplicable,

        String gstType,

        Boolean tdsApplicable,

        String tdsSection,

        Map<String, Object> extendedAttributes
) {
}
