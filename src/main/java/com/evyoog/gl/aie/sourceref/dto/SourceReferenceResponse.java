package com.evyoog.gl.aie.sourceref.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SourceReferenceResponse(
        UUID id,
        UUID journalHeaderId,
        String journalNumber,
        String sourceSystem,
        String sourceDocumentType,
        String sourceDocumentId,
        String sourceDocumentRef,
        Integer sourceLineNumber,
        BigDecimal amount,
        Instant createdAt,
        String createdBy) {
}
