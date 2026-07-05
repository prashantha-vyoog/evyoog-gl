package com.evyoog.gl.audit.service;

import com.evyoog.gl.approval.domain.JournalApprovalLog;
import com.evyoog.gl.approval.repository.JournalApprovalLogRepository;
import com.evyoog.gl.audit.dto.AuditTrailPageResponse;
import com.evyoog.gl.audit.dto.AuditTrailResponse;
import com.evyoog.gl.audit.mapper.AuditTrailMapper;
import com.evyoog.gl.common.audit.domain.AuditAction;
import com.evyoog.gl.common.audit.domain.AuditLog;
import com.evyoog.gl.common.audit.repository.AuditLogRepository;
import com.evyoog.gl.common.exception.EvyoogException;
import com.evyoog.gl.posting.domain.JournalHeader;
import com.evyoog.gl.posting.repository.JournalHeaderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GL-29 Audit Trail — read-only query layer over the audit_log table already
 * written by AuditService (GL-12, GL-13, GL-14, GL-16). No new write paths.
 */
@Service
@RequiredArgsConstructor
public class AuditTrailService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String JOURNAL_HEADER_ENTITY = "journal_header";

    private final AuditLogRepository auditLogRepository;
    private final JournalApprovalLogRepository approvalLogRepository;
    private final JournalHeaderRepository journalHeaderRepository;
    private final AuditTrailMapper mapper;

    @Transactional(readOnly = true)
    public AuditTrailPageResponse search(String entityType, UUID entityId, String performedBy, String action,
                                          Instant from, Instant to, int page, int size) {
        if (entityType == null && entityId == null && performedBy == null && action == null
                && from == null && to == null) {
            throw new EvyoogException("AUDIT_FILTER_REQUIRED",
                    "At least one filter (entityType, entityId, performedBy, action, from, to) is required.",
                    HttpStatus.BAD_REQUEST);
        }
        AuditAction actionEnum = parseAction(action);
        Specification<AuditLog> spec = AuditLogSpecifications.withFilters(
                entityType, entityId, performedBy, actionEnum, from, to);
        return toPageResponse(auditLogRepository.findAll(spec, buildPageable(page, size)));
    }

    @Transactional(readOnly = true)
    public AuditTrailPageResponse getByEntity(String entityType, UUID entityId, int page, int size) {
        return toPageResponse(
                auditLogRepository.findByEntityNameAndEntityId(entityType, entityId, buildPageable(page, size)));
    }

    @Transactional(readOnly = true)
    public AuditTrailPageResponse getByUser(String performedBy, Instant from, Instant to, int page, int size) {
        Specification<AuditLog> spec = AuditLogSpecifications.withFilters(null, null, performedBy, null, from, to);
        return toPageResponse(auditLogRepository.findAll(spec, buildPageable(page, size)));
    }

    @Transactional(readOnly = true)
    public AuditTrailPageResponse getByDateRange(Instant from, Instant to, String entityType, int page, int size) {
        Specification<AuditLog> spec = AuditLogSpecifications.withFilters(entityType, null, null, null, from, to);
        return toPageResponse(auditLogRepository.findAll(spec, buildPageable(page, size)));
    }

    @Transactional(readOnly = true)
    public AuditTrailPageResponse getByAction(String action, String entityType, int page, int size) {
        AuditAction actionEnum = parseAction(action);
        Specification<AuditLog> spec = AuditLogSpecifications.withFilters(entityType, null, null, actionEnum, null, null);
        return toPageResponse(auditLogRepository.findAll(spec, buildPageable(page, size)));
    }

    /**
     * Aggregates the full lifecycle audit view for a journal: audit_log entries
     * for the journal itself, audit_log entries for its reversal counterpart
     * (whichever direction the reversal relationship runs), and the GL-12
     * approval log — all merged into one chronological timeline.
     */
    @Transactional(readOnly = true)
    public List<AuditTrailResponse> getJournalAuditHistory(UUID journalId) {
        JournalHeader journal = journalHeaderRepository.findById(journalId)
                .orElseThrow(() -> new EvyoogException("JOURNAL_NOT_FOUND",
                        "Journal not found: " + journalId, HttpStatus.NOT_FOUND));

        List<AuditTrailResponse> events = new ArrayList<>();
        events.addAll(toResponses(auditLogRepository
                .findByEntityNameAndEntityIdOrderByPerformedAtAsc(JOURNAL_HEADER_ENTITY, journalId)));

        UUID pairedJournalId = journal.getReversalOf() != null
                ? journal.getReversalOf().getId()
                : journalHeaderRepository.findByReversalOfId(journalId).map(JournalHeader::getId).orElse(null);
        if (pairedJournalId != null) {
            events.addAll(toResponses(auditLogRepository
                    .findByEntityNameAndEntityIdOrderByPerformedAtAsc(JOURNAL_HEADER_ENTITY, pairedJournalId)));
        }

        approvalLogRepository.findByJournalHeaderIdOrderByCreatedAtAsc(journalId).stream()
                .map(this::toApprovalAuditEntry)
                .forEach(events::add);

        events.sort(Comparator.comparing(AuditTrailResponse::createdAt));
        return events;
    }

    private AuditAction parseAction(String action) {
        if (action == null) {
            return null;
        }
        try {
            return AuditAction.valueOf(action.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new EvyoogException("INVALID_AUDIT_ACTION",
                    "Unknown audit action: " + action, HttpStatus.BAD_REQUEST);
        }
    }

    private Pageable buildPageable(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "performedAt"));
    }

    private List<AuditTrailResponse> toResponses(List<AuditLog> logs) {
        return logs.stream().map(mapper::toResponse).toList();
    }

    private AuditTrailPageResponse toPageResponse(Page<AuditLog> page) {
        return AuditTrailPageResponse.builder()
                .content(page.getContent().stream().map(mapper::toResponse).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    private AuditTrailResponse toApprovalAuditEntry(JournalApprovalLog log) {
        return AuditTrailResponse.builder()
                .id(log.getId())
                .entityType("journal_approval_log")
                .entityId(log.getJournalHeader().getId())
                .action(log.getAction())
                .performedBy(log.getPerformedBy())
                .beforeValue(Map.of("status", log.getFromStatus()))
                .afterValue(Map.of("status", log.getToStatus(), "comments",
                        log.getComments() == null ? "" : log.getComments()))
                .createdAt(log.getCreatedAt())
                .build();
    }
}
