package com.evyoog.gl.approval.service;

import com.evyoog.gl.approval.domain.JournalApprovalLog;
import com.evyoog.gl.approval.dto.ApprovalActionRequest;
import com.evyoog.gl.approval.dto.ApprovalResponse;
import com.evyoog.gl.approval.dto.JournalApprovalLogResponse;
import com.evyoog.gl.approval.repository.JournalApprovalLogRepository;
import com.evyoog.gl.common.audit.domain.AuditAction;
import com.evyoog.gl.common.audit.service.AuditService;
import com.evyoog.gl.common.exception.EvyoogException;
import com.evyoog.gl.journal.dto.JournalSummaryResponse;
import com.evyoog.gl.journal.mapper.JournalMapper;
import com.evyoog.gl.ledger.domain.FinanceMode;
import com.evyoog.gl.posting.domain.JournalHeader;
import com.evyoog.gl.posting.domain.JournalLine;
import com.evyoog.gl.posting.domain.JournalStatus;
import com.evyoog.gl.posting.dto.PostingLineRequest;
import com.evyoog.gl.posting.dto.PostingRequest;
import com.evyoog.gl.posting.repository.JournalHeaderRepository;
import com.evyoog.gl.posting.service.PostingEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * GL-12 Journal Approval. Depends on GL-11's JournalHeader lifecycle and the
 * PostingEngine (GL-15) — approve() finalises a journal by delegating to the
 * same posting path used everywhere else. Approval is THICK-mode only per
 * Rule 8; THIN skips the approval gate entirely.
 */
@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final JournalHeaderRepository journalHeaderRepository;
    private final JournalApprovalLogRepository approvalLogRepository;
    private final PostingEngine postingEngine;
    private final JournalMapper journalMapper;
    private final AuditService auditService;

    @Transactional
    public ApprovalResponse approve(UUID journalId, ApprovalActionRequest request) {
        JournalHeader journal = findAndValidate(journalId, JournalStatus.PENDING_APPROVAL, "approve");
        validateThickMode(journal);

        logAction(journal, "APPROVED", JournalStatus.PENDING_APPROVAL, JournalStatus.APPROVED,
                request.performedBy(), request.comments());

        journal.setStatus(JournalStatus.APPROVED);
        journal.setUpdatedBy(request.performedBy());
        JournalHeader saved = journalHeaderRepository.save(journal);
        auditService.log(AuditAction.UPDATE, "journal_header", saved.getId(), null,
                journalAuditSnapshot(saved), request.performedBy());

        PostingRequest postingRequest = buildPostingRequest(journal, request.performedBy());
        postingEngine.post(postingRequest);

        logAction(journal, "APPROVED", JournalStatus.APPROVED, JournalStatus.POSTED,
                request.performedBy(), "Auto-posted after approval");

        return buildResponse(journal, JournalStatus.POSTED, "Journal approved and posted successfully.");
    }

    @Transactional
    public ApprovalResponse reject(UUID journalId, ApprovalActionRequest request) {
        JournalHeader journal = findAndValidate(journalId, JournalStatus.PENDING_APPROVAL, "reject");
        validateThickMode(journal);

        logAction(journal, "REJECTED", JournalStatus.PENDING_APPROVAL, JournalStatus.DRAFT,
                request.performedBy(), request.comments());

        journal.setStatus(JournalStatus.DRAFT);
        journal.setUpdatedBy(request.performedBy());
        JournalHeader saved = journalHeaderRepository.save(journal);
        auditService.log(AuditAction.UPDATE, "journal_header", saved.getId(), null,
                journalAuditSnapshot(saved), request.performedBy());

        return buildResponse(journal, JournalStatus.DRAFT, "Journal rejected and returned to DRAFT.");
    }

    @Transactional
    public ApprovalResponse recall(UUID journalId, ApprovalActionRequest request) {
        JournalHeader journal = findAndValidate(journalId, JournalStatus.PENDING_APPROVAL, "recall");

        logAction(journal, "RECALLED", JournalStatus.PENDING_APPROVAL, JournalStatus.DRAFT,
                request.performedBy(), request.comments());

        journal.setStatus(JournalStatus.DRAFT);
        journal.setUpdatedBy(request.performedBy());
        JournalHeader saved = journalHeaderRepository.save(journal);
        auditService.log(AuditAction.UPDATE, "journal_header", saved.getId(), null,
                journalAuditSnapshot(saved), request.performedBy());

        return buildResponse(journal, JournalStatus.DRAFT, "Journal recalled to DRAFT.");
    }

    @Transactional(readOnly = true)
    public List<JournalApprovalLogResponse> getApprovalHistory(UUID journalId) {
        return approvalLogRepository.findByJournalHeaderIdOrderByCreatedAtAsc(journalId).stream()
                .map(this::toLogResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<JournalSummaryResponse> getPendingApprovals(UUID legalEntityId) {
        return journalHeaderRepository.findByLegalEntityIdAndStatus(legalEntityId, JournalStatus.PENDING_APPROVAL).stream()
                .map(journalMapper::toSummary)
                .collect(Collectors.toList());
    }

    private JournalHeader findAndValidate(UUID journalId, JournalStatus requiredStatus, String action) {
        JournalHeader journal = journalHeaderRepository.findById(journalId)
                .orElseThrow(() -> new EvyoogException("JOURNAL_NOT_FOUND",
                        "Journal not found: " + journalId, HttpStatus.NOT_FOUND));

        if (journal.getStatus() != requiredStatus) {
            throw new EvyoogException("INVALID_APPROVAL_STATE",
                    "Cannot " + action + " a journal in " + journal.getStatus() + " status. " +
                            "Required status: " + requiredStatus);
        }
        return journal;
    }

    private void validateThickMode(JournalHeader journal) {
        if (journal.getFinanceModeSnapshot() != FinanceMode.THICK) {
            throw new EvyoogException("THICK_MODE_ONLY",
                    "Approval workflow is only available for THICK mode journals.");
        }
    }

    private void logAction(JournalHeader journal, String action, JournalStatus from, JournalStatus to,
                            String performedBy, String comments) {
        JournalApprovalLog log = JournalApprovalLog.builder()
                .journalHeader(journal)
                .action(action)
                .performedBy(performedBy)
                .comments(comments)
                .fromStatus(from.name())
                .toStatus(to.name())
                .build();
        approvalLogRepository.save(log);
    }

    private PostingRequest buildPostingRequest(JournalHeader journal, String performedBy) {
        List<PostingLineRequest> lines = journal.getLines().stream()
                .map(this::toPostingLineRequest)
                .toList();

        return PostingRequest.builder()
                .legalEntityId(journal.getLegalEntity().getId())
                .accountingPeriodId(journal.getAccountingPeriod().getId())
                .journalSourceId(journal.getJournalSource().getId())
                .journalCategoryId(journal.getJournalCategory().getId())
                .description(journal.getDescription())
                .glDate(journal.getGlDate())
                .accountingDate(journal.getAccountingDate())
                .currencyCode(journal.getCurrencyCode())
                .exchangeRate(journal.getExchangeRate())
                .lines(lines)
                .performedBy(performedBy)
                .externalReference(journal.getExternalReference())
                .initialStatus(JournalStatus.APPROVED)
                .build();
    }

    private PostingLineRequest toPostingLineRequest(JournalLine line) {
        return PostingLineRequest.builder()
                .accountCombination(line.getAccountCombination())
                .naturalAccountValueId(line.getNaturalAccount().getId())
                .debitAmount(line.getDebitAmount())
                .creditAmount(line.getCreditAmount())
                .currencyCode(line.getCurrencyCode())
                .description(line.getDescription())
                .gstApplicable(line.getGstApplicable())
                .gstType(line.getGstType())
                .tdsApplicable(line.getTdsApplicable())
                .tdsSection(line.getTdsSection())
                .build();
    }

    private JournalApprovalLogResponse toLogResponse(JournalApprovalLog log) {
        return JournalApprovalLogResponse.builder()
                .id(log.getId())
                .journalHeaderId(log.getJournalHeader().getId())
                .journalNumber(log.getJournalHeader().getJournalNumber())
                .action(log.getAction())
                .performedBy(log.getPerformedBy())
                .comments(log.getComments())
                .fromStatus(log.getFromStatus())
                .toStatus(log.getToStatus())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private Map<String, Object> journalAuditSnapshot(JournalHeader header) {
        return Map.of(
                "journalNumber", header.getJournalNumber(),
                "status", header.getStatus(),
                "financeModeSnapshot", header.getFinanceModeSnapshot());
    }

    private ApprovalResponse buildResponse(JournalHeader journal, JournalStatus newStatus, String message) {
        return ApprovalResponse.builder()
                .journalHeaderId(journal.getId())
                .journalNumber(journal.getJournalNumber())
                .newStatus(newStatus)
                .message(message)
                .approvalHistory(getApprovalHistory(journal.getId()))
                .build();
    }
}
