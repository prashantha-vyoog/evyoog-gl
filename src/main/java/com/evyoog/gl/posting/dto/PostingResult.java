package com.evyoog.gl.posting.dto;

import com.evyoog.gl.ledger.domain.FinanceMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Returned by the Posting Engine (GL-15) to its callers (GL-11, GL-16).
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostingResult {

    private boolean success;
    private UUID journalHeaderId;
    private String journalNumber;
    private UUID slaEventLogId;
    private FinanceMode modeUsed;
    private String message;

    public static PostingResult posted(UUID journalHeaderId, String journalNumber, FinanceMode modeUsed) {
        return PostingResult.builder()
                .success(true)
                .journalHeaderId(journalHeaderId)
                .journalNumber(journalNumber)
                .modeUsed(modeUsed)
                .build();
    }

    public static PostingResult eventEmitted(UUID slaEventLogId) {
        return PostingResult.builder()
                .success(true)
                .slaEventLogId(slaEventLogId)
                .modeUsed(FinanceMode.EVENT_ONLY)
                .build();
    }

    public static PostingResult failed(String reason) {
        return PostingResult.builder()
                .success(false)
                .message(reason)
                .build();
    }
}
