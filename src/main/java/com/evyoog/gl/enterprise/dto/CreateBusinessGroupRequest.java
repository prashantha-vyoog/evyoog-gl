package com.evyoog.gl.enterprise.dto;

import com.evyoog.gl.enterprise.domain.EsMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateBusinessGroupRequest(

        @NotNull(message = "consumptionContextId is required")
        UUID consumptionContextId,

        @NotBlank(message = "code is required")
        @Size(max = 50, message = "code must be at most 50 characters")
        String code,

        @NotBlank(message = "name is required")
        @Size(max = 200, message = "name must be at most 200 characters")
        String name,

        @NotNull(message = "esMode is required")
        EsMode esMode,

        @NotBlank(message = "defaultCurrency is required")
        @Size(min = 3, max = 3, message = "defaultCurrency must be a 3-letter ISO currency code")
        String defaultCurrency
) {
}
