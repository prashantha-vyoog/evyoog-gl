package com.evyoog.gl.enterprise.dto;

import com.evyoog.gl.enterprise.domain.AccountingStandard;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateLegalEntityRequest(

        @NotNull(message = "businessGroupId is required")
        UUID businessGroupId,

        @NotBlank(message = "code is required")
        @Size(max = 50, message = "code must be at most 50 characters")
        String code,

        @NotBlank(message = "name is required")
        @Size(max = 200, message = "name must be at most 200 characters")
        String name,

        AccountingStandard accountingStandard,

        @Size(max = 10, message = "tan must be at most 10 characters")
        String tan
) {
}
