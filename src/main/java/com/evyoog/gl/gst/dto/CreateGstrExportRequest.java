package com.evyoog.gl.gst.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record CreateGstrExportRequest(

        @NotNull(message = "legalEntityId is required")
        UUID legalEntityId,

        @NotNull(message = "periodId is required")
        UUID periodId,

        @NotBlank(message = "returnType is required")
        @Pattern(regexp = "GSTR1|GSTR3B", message = "returnType must be GSTR1 or GSTR3B")
        String returnType
) {
}
