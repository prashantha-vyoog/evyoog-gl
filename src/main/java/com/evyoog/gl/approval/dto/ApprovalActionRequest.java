package com.evyoog.gl.approval.dto;

import jakarta.validation.constraints.NotBlank;

public record ApprovalActionRequest(
        @NotBlank String performedBy,
        String comments
) {
}
