package com.evyoog.gl.aie.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Map;

public record AieLineRequest(
        @NotNull(message = "lineNumber is required")
        Integer lineNumber,

        @NotBlank(message = "accountCode is required")
        String accountCode,

        // Non-natural-account dimension values, keyed by DimensionType name.
        // Resolved and validated by the Posting Engine, not by this pipeline.
        Map<String, String> accountCombination,

        BigDecimal debitAmount,

        BigDecimal creditAmount,

        String description,

        String gstType,

        Boolean gstApplicable,

        String tdsSection,

        Boolean tdsApplicable
) {
}
