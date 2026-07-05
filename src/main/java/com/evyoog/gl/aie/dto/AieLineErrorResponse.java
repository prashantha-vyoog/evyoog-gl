package com.evyoog.gl.aie.dto;

import lombok.Builder;

@Builder
public record AieLineErrorResponse(
        Integer lineNumber,
        String errorCode,
        String errorMessage,
        String errorStage,
        String fieldName
) {
}
