package com.evyoog.gl.journal.dto;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record JournalLineResponse(
        UUID id,
        Integer lineNumber,
        Map<String, String> accountCombination,
        UUID naturalAccountValueId,
        String accountCode,
        String accountName,
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
