package com.evyoog.gl.gst.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record GstrSummaryResponse(
        UUID legalEntityId,
        String legalEntityName,
        String gstin,
        UUID accountingPeriodId,
        String periodName,
        String fiscalYear,

        BigDecimal totalCgstCollected,
        BigDecimal totalSgstCollected,
        BigDecimal totalIgstCollected,
        BigDecimal totalUtgstCollected,
        BigDecimal totalGstCollected,

        BigDecimal totalInputCgst,
        BigDecimal totalInputSgst,
        BigDecimal totalInputIgst,
        BigDecimal totalInputUtgst,
        BigDecimal totalInputTax,

        BigDecimal netCgstPayable,
        BigDecimal netSgstPayable,
        BigDecimal netIgstPayable,
        BigDecimal netUtgstPayable,
        BigDecimal netTaxPayable
) {
}
