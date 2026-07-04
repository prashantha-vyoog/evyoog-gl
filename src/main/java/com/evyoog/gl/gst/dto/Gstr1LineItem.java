package com.evyoog.gl.gst.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Builder
public record Gstr1LineItem(
        UUID journalHeaderId,
        String journalNumber,
        LocalDate glDate,
        String gstType,
        String placeOfSupply,
        BigDecimal cgstAmount,
        BigDecimal sgstAmount,
        BigDecimal igstAmount,
        BigDecimal utgstAmount,
        BigDecimal totalTax,
        Boolean isReverseCharge
) {
}
