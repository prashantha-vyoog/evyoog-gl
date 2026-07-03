package com.evyoog.gl.coa.dto;

import com.evyoog.gl.coa.domain.IndustryType;
import com.evyoog.gl.ledger.domain.FinanceMode;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ProvisioningTemplateResponse(
        UUID id,
        String code,
        String name,
        String description,
        IndustryType industryType,
        FinanceMode financeMode,
        Map<String, Object> templateData,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {
}
