package com.evyoog.gl.wizard.dto;

import com.evyoog.gl.enterprise.domain.AccountingStandard;
import com.evyoog.gl.enterprise.domain.EsMode;
import com.evyoog.gl.wizard.domain.SessionPurpose;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WizardProvisioningResponse(
        UUID contextId,
        UUID businessGroupId,
        String businessGroupName,
        EsMode esMode,
        UUID legalEntityId,
        String legalEntityName,
        AccountingStandard accountingStandard,
        List<ProvisionedBusinessUnit> businessUnits,
        Integer fiscalStartMonth,
        Integer fiscalStartYear,
        Boolean hasOwnCoa,
        String industryTemplate,
        SessionPurpose sessionPurpose,
        Instant completedAt
) {
}
