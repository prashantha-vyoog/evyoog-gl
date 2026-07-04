package com.evyoog.gl.journal.dto;

public record UpdateJournalRequest(
        String description,
        String notes
) {
}
