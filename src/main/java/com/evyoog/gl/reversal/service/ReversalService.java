package com.evyoog.gl.reversal.service;

import com.evyoog.gl.common.audit.domain.AuditAction;
import com.evyoog.gl.common.audit.service.AuditService;
import com.evyoog.gl.common.exception.EvyoogException;
import com.evyoog.gl.ledger.domain.FinanceMode;
import com.evyoog.gl.posting.domain.JournalCategory;
import com.evyoog.gl.posting.domain.JournalHeader;
import com.evyoog.gl.posting.domain.JournalSource;
import com.evyoog.gl.posting.domain.JournalStatus;
import com.evyoog.gl.posting.dto.PostingLineRequest;
import com.evyoog.gl.posting.dto.PostingRequest;
import com.evyoog.gl.posting.dto.PostingResult;
import com.evyoog.gl.posting.repository.JournalCategoryRepository;
import com.evyoog.gl.posting.repository.JournalHeaderRepository;
import com.evyoog.gl.posting.repository.JournalSourceRepository;
import com.evyoog.gl.posting.service.PostingEngine;
import com.evyoog.gl.reversal.dto.ReversalRequest;
import com.evyoog.gl.reversal.dto.ReversalResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GL-13 Journal Reversal. Depends on GL-11's JournalHeader lifecycle and the
 * PostingEngine (GL-15) — reverse() posts a brand-new journal with all DR/CR
 * lines flipped via {@link PostingEngine#post(PostingRequest)}, then marks
 * the source journal REVERSED. Reversal is THICK-mode only, mirroring the
 * GL-12 Approval gate. The period-open gate is enforced by the PostingEngine
 * itself — this service does not re-check it.
 */
@Service
@RequiredArgsConstructor
public class ReversalService {

    private static final String REVERSAL_CODE = "REVERSAL";

    private final JournalHeaderRepository journalHeaderRepository;
    private final JournalSourceRepository journalSourceRepository;
    private final JournalCategoryRepository journalCategoryRepository;
    private final PostingEngine postingEngine;
    private final AuditService auditService;

    @Transactional
    public ReversalResponse reverse(UUID journalId, ReversalRequest request) {
        JournalHeader source = findJournal(journalId);

        if (source.getStatus() != JournalStatus.POSTED) {
            throw new EvyoogException("INVALID_REVERSAL_STATE",
                    "Only POSTED journals can be reversed. Current status: " + source.getStatus());
        }
        if (source.getFinanceModeSnapshot() != FinanceMode.THICK) {
            throw new EvyoogException("THICK_MODE_ONLY",
                    "Journal reversal is only available for THICK mode journals.");
        }
        if (journalHeaderRepository.existsByReversalOfId(journalId)) {
            throw new EvyoogException("ALREADY_REVERSED", "This journal has already been reversed.");
        }

        PostingResult result = postingEngine.post(buildReversalPostingRequest(source, request));

        JournalHeader reversalJournal = findJournal(result.getJournalHeaderId());
        reversalJournal.setIsReversal(true);
        reversalJournal.setReversalOf(source);
        JournalHeader savedReversal = journalHeaderRepository.save(reversalJournal);
        auditService.log(AuditAction.UPDATE, "journal_header", savedReversal.getId(), null,
                journalAuditSnapshot(savedReversal), request.reversedBy());

        source.setStatus(JournalStatus.REVERSED);
        source.setUpdatedBy(request.reversedBy());
        JournalHeader savedSource = journalHeaderRepository.save(source);
        auditService.log(AuditAction.UPDATE, "journal_header", savedSource.getId(), null,
                journalAuditSnapshot(savedSource), request.reversedBy());

        return buildResponse(savedSource, savedReversal, "Journal reversed successfully.");
    }

    @Transactional(readOnly = true)
    public ReversalResponse getReversalDetails(UUID journalId) {
        JournalHeader source = findJournal(journalId);
        JournalHeader reversal = journalHeaderRepository.findByReversalOfId(journalId)
                .orElseThrow(() -> new EvyoogException("REVERSAL_NOT_FOUND",
                        "No reversal found for journal: " + journalId, HttpStatus.NOT_FOUND));

        return buildResponse(source, reversal, "Reversal details retrieved.");
    }

    private PostingRequest buildReversalPostingRequest(JournalHeader source, ReversalRequest request) {
        JournalSource reversalSource = journalSourceRepository.findByCode(REVERSAL_CODE)
                .orElseThrow(() -> new EvyoogException("REVERSAL_SOURCE_NOT_CONFIGURED",
                        "No '" + REVERSAL_CODE + "' Journal Source configured."));
        JournalCategory reversalCategory = journalCategoryRepository.findByCode(REVERSAL_CODE)
                .orElseThrow(() -> new EvyoogException("REVERSAL_CATEGORY_NOT_CONFIGURED",
                        "No '" + REVERSAL_CODE + "' Journal Category configured."));

        List<PostingLineRequest> reversedLines = source.getLines().stream()
                .map(line -> PostingLineRequest.builder()
                        .accountCombination(line.getAccountCombination())
                        .naturalAccountValueId(line.getNaturalAccount().getId())
                        .debitAmount(line.getCreditAmount())
                        .creditAmount(line.getDebitAmount())
                        .currencyCode(line.getCurrencyCode())
                        .description(line.getDescription())
                        .gstApplicable(line.getGstApplicable())
                        .gstType(line.getGstType())
                        .tdsApplicable(line.getTdsApplicable())
                        .tdsSection(line.getTdsSection())
                        .build())
                .toList();

        LocalDate today = LocalDate.now();
        String description = "Reversal of " + source.getJournalNumber()
                + (request.reason() != null ? " — " + request.reason() : "");

        return PostingRequest.builder()
                .legalEntityId(source.getLegalEntity().getId())
                .accountingPeriodId(request.targetPeriodId())
                .journalSourceId(reversalSource.getId())
                .journalCategoryId(reversalCategory.getId())
                .description(description)
                .glDate(today)
                .accountingDate(today)
                .currencyCode(source.getCurrencyCode())
                .exchangeRate(source.getExchangeRate())
                .lines(reversedLines)
                .performedBy(request.reversedBy())
                .build();
    }

    private JournalHeader findJournal(UUID id) {
        return journalHeaderRepository.findById(id)
                .orElseThrow(() -> new EvyoogException("JOURNAL_NOT_FOUND",
                        "Journal not found: " + id, HttpStatus.NOT_FOUND));
    }

    private Map<String, Object> journalAuditSnapshot(JournalHeader header) {
        return Map.of(
                "journalNumber", header.getJournalNumber(),
                "status", header.getStatus(),
                "isReversal", header.getIsReversal());
    }

    private ReversalResponse buildResponse(JournalHeader source, JournalHeader reversal, String message) {
        return ReversalResponse.builder()
                .originalJournalId(source.getId())
                .originalJournalNumber(source.getJournalNumber())
                .reversalJournalId(reversal.getId())
                .reversalJournalNumber(reversal.getJournalNumber())
                .originalStatus(source.getStatus())
                .reversalStatus(reversal.getStatus())
                .message(message)
                .build();
    }
}
