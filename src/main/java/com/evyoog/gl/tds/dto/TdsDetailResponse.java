package com.evyoog.gl.tds.dto;

import lombok.Builder;

import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

@Builder
public record TdsDetailResponse(
        UUID legalEntityId,
        String legalEntityName,
        String periodName,
        String tdsSection,
        String sectionDescription,
        List<TdsLineItem> lines,
        BigDecimal totalTds,
        Integer lineCount
) {
}
