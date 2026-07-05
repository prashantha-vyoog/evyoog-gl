package com.evyoog.gl.recurring.dto;

import jakarta.validation.constraints.NotBlank;

public record DeactivateTemplateRequest(
        @NotBlank String deactivatedBy
) {
}
