package com.evyoog.gl.enterprise.dto;

import com.evyoog.gl.enterprise.domain.SegmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateConsumptionContextRequest(

        @NotNull(message = "segmentType is required")
        SegmentType segmentType,

        @NotBlank(message = "code is required")
        @Size(max = 50, message = "code must be at most 50 characters")
        String code,

        @NotBlank(message = "name is required")
        @Size(max = 200, message = "name must be at most 200 characters")
        String name
) {
}
