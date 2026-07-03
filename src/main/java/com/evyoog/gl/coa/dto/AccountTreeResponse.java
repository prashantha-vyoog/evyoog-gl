package com.evyoog.gl.coa.dto;

import java.util.List;
import java.util.UUID;

public record AccountTreeResponse(
        UUID ledgerId,
        UUID financeDimensionId,
        long totalCount,
        long postableCount,
        long summaryCount,
        List<AccountResponse> accounts
) {
}
