package com.evyoog.gl.dimension.dto;

import com.evyoog.gl.dimension.domain.AccountQualifier;
import com.evyoog.gl.dimension.domain.DimensionType;
import com.evyoog.gl.dimension.domain.NormalBalance;

import java.time.Instant;
import java.util.UUID;

public record DimensionValueResponse(
        UUID id,
        UUID financeDimensionId,
        String dimensionCode,
        String dimensionName,
        DimensionType dimensionType,
        String code,
        String name,
        String description,
        UUID parentValueId,
        String parentValueCode,
        String parentValueName,
        AccountQualifier accountQualifier,
        boolean isSummary,
        boolean isPostable,
        NormalBalance normalBalance,
        boolean gstApplicable,
        boolean tdsApplicable,
        String tdsSection,
        int displayOrder,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {
}
