package com.evyoog.gl.coa.dto;

import com.evyoog.gl.dimension.domain.AccountQualifier;
import com.evyoog.gl.dimension.domain.NormalBalance;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String code,
        String name,
        String description,
        UUID parentAccountId,
        String parentAccountCode,
        String parentAccountName,
        AccountQualifier qualifier,
        Boolean isSummary,
        Boolean isPostable,
        NormalBalance normalBalance,
        Boolean gstApplicable,
        Boolean tdsApplicable,
        String tdsSection,
        UUID counterpartyLegalEntityId,
        String counterpartyLegalEntityName,
        String ccManagerName,
        String ccManagerEmail,
        String ccDepartment,
        LocalDate validFrom,
        LocalDate validTo,
        Boolean budgetControlled,
        Map<String, Object> extendedAttributes,
        Integer displayOrder,
        Boolean isActive,
        List<AccountResponse> children,
        Instant createdAt
) {
    public AccountResponse withChildren(List<AccountResponse> newChildren) {
        return new AccountResponse(id, code, name, description, parentAccountId, parentAccountCode,
                parentAccountName, qualifier, isSummary, isPostable, normalBalance, gstApplicable, tdsApplicable,
                tdsSection, counterpartyLegalEntityId, counterpartyLegalEntityName, ccManagerName, ccManagerEmail,
                ccDepartment, validFrom, validTo, budgetControlled, extendedAttributes, displayOrder, isActive,
                newChildren, createdAt);
    }
}
