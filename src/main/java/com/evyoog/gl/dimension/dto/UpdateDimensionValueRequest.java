package com.evyoog.gl.dimension.dto;

import jakarta.validation.constraints.Size;

public record UpdateDimensionValueRequest(

        @Size(max = 255, message = "name must be at most 255 characters")
        String name,

        @Size(max = 500, message = "description must be at most 500 characters")
        String description,

        Boolean isSummary,

        Boolean isPostable,

        Boolean gstApplicable,

        Boolean tdsApplicable,

        @Size(max = 10, message = "tdsSection must be at most 10 characters")
        String tdsSection,

        Integer displayOrder
) {
}
