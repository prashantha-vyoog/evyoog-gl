package com.evyoog.gl.enterprise.dto;

import com.evyoog.gl.enterprise.domain.AccountingStandard;

import java.time.Instant;
import java.util.UUID;

public record LegalEntityResponse(
        UUID id,
        UUID businessGroupId,
        String code,
        String name,
        AccountingStandard accountingStandard,
        String tan,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {
}
