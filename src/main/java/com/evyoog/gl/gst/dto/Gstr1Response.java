package com.evyoog.gl.gst.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record Gstr1Response(
        UUID legalEntityId,
        String legalEntityName,
        String gstin,
        UUID accountingPeriodId,
        String periodName,
        String fiscalYear,
        List<Gstr1LineItem> outwardSupplies,
        BigDecimal totalTax,
        Integer transactionCount
) {
}
