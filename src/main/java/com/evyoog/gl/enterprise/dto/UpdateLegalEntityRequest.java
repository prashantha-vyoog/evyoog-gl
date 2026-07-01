package com.evyoog.gl.enterprise.dto;

import com.evyoog.gl.enterprise.domain.AccountingStandard;
import jakarta.validation.constraints.Size;

public record UpdateLegalEntityRequest(

        @Size(max = 200, message = "name must be at most 200 characters")
        String name,

        AccountingStandard accountingStandard,

        @Size(max = 10, message = "tan must be at most 10 characters")
        String tan
) {
}
