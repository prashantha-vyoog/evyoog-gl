package com.evyoog.gl.aie.sourceref.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateSourceReferenceRequest(
        @NotNull UUID journalHeaderId,
        @NotBlank String sourceSystem,
        @NotBlank String sourceDocumentType,
        @NotBlank String sourceDocumentId,
        String sourceDocumentRef,
        Integer sourceLineNumber,
        BigDecimal amount,
        String createdBy) {
}
