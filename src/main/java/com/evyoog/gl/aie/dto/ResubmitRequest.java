package com.evyoog.gl.aie.dto;

import jakarta.validation.constraints.NotBlank;

public record ResubmitRequest(
        @NotBlank(message = "resubmittedBy is required")
        String resubmittedBy
) {
}
