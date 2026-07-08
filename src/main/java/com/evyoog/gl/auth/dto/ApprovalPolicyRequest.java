package com.evyoog.gl.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ApprovalPolicyRequest(

        @NotNull(message = "legalEntityId is required")
        UUID legalEntityId,

        UUID businessUnitId,

        UUID inventoryOrgId,

        @NotBlank(message = "journalSourceCode is required")
        String journalSourceCode,

        @NotNull(message = "requiresApproval is required")
        Boolean requiresApproval,

        BigDecimal approvalThresholdAmount,

        String approverRoleCode
) {
}
