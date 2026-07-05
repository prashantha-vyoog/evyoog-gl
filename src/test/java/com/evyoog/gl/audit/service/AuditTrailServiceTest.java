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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditTrailServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private JournalApprovalLogRepository approvalLogRepository;
    @Mock
    private JournalHeaderRepository journalHeaderRepository;
    @Mock
    private AuditTrailMapper mapper;
    @InjectMocks
    private AuditTrailService service;

    @Test
    void search_noFiltersProvided_throws400() {
        assertThatThrownBy(() -> service.search(null, null, null, null, null, null, 0, 20))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "AUDIT_FILTER_REQUIRED");
    }

    @Test
    void search_invalidAction_throws400() {
        assertThatThrownBy(() -> service.search(null, null, null, "NOT_A_REAL_ACTION", null, null, 0, 20))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_AUDIT_ACTION");
    }

    @Test
    void search_withEntityTypeFilter_delegatesToSpecificationQuery() {
        AuditLog log = auditLog("journal_header", UUID.randomUUID(), AuditAction.CREATE, "creator1");
        stubPage(List.of(log));
        stubMapper(log);

        AuditTrailPageResponse response = service.search("journal_header", null, null, null, null, null, 0, 20);

        assertThat(response.content()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    void getByEntity_returnsChronologicalHistory() {
        UUID entityId = UUID.randomUUID();
        AuditLog createLog = auditLog("journal_header", entityId, AuditAction.CREATE, "creator1");
        AuditLog updateLog = auditLog("journal_header", entityId, AuditAction.UPDATE, "updater1");
        when(auditLogRepository.findByEntityNameAndEntityId(eq("journal_header"), eq(entityId), any()))
                .thenAnswer(inv -> new org.springframework.data.domain.PageImpl<>(
                        List.of(createLog, updateLog), inv.getArgument(2), 2));
        stubMapper(createLog);
        stubMapper(updateLog);

        AuditTrailPageResponse response = service.getByEntity("journal_header", entityId, 0, 20);

        assertThat(response.content()).hasSize(2);
        assertThat(response.totalElements()).isEqualTo(2);
    }

    @Test
    void getByEntity_unknownEntity_returnsEmptyPage() {
        UUID entityId = UUID.randomUUID();
        when(auditLogRepository.findByEntityNameAndEntityId(eq("journal_header"), eq(entityId), any()))
                .thenAnswer(inv -> new org.springframework.data.domain.PageImpl<>(
                        List.of(), inv.getArgument(2), 0));

        AuditTrailPageResponse response = service.getByEntity("journal_header", entityId, 0, 20);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
    }

    @Test
    void getByEntity_paginationCorrect() {
        UUID entityId = UUID.randomUUID();
        when(auditLogRepository.findByEntityNameAndEntityId(eq("journal_header"), eq(entityId), any()))
                .thenAnswer(inv -> new org.springframework.data.domain.PageImpl<>(
                        List.of(), inv.getArgument(2), 45));

        AuditTrailPageResponse response = service.getByEntity("journal_header", entityId, 1, 10);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(45);
        assertThat(response.totalPages()).isEqualTo(5);
        assertThat(response.last()).isFalse();
    }

    @Test
    void getByUser_returnsUserActions() {
        AuditLog log = auditLog("journal_header", UUID.randomUUID(), AuditAction.UPDATE, "approver1");
        stubPage(List.of(log));
        stubMapper(log);

        AuditTrailPageResponse response = service.getByUser("approver1", null, null, 0, 20);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).performedBy()).isEqualTo("approver1");
    }

    @Test
    void getByDateRange_returnsCorrectWindow() {
        AuditLog log = auditLog("journal_header", UUID.randomUUID(), AuditAction.CREATE, "creator1");
        stubPage(List.of(log));
        stubMapper(log);

        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-31T23:59:59Z");
        AuditTrailPageResponse response = service.getByDateRange(from, to, "journal_header", 0, 20);

        assertThat(response.content()).hasSize(1);
    }

    @Test
    void getByAction_filtersCorrectly() {
        AuditLog log = auditLog("journal_header", UUID.randomUUID(), AuditAction.DELETE, "deleter1");
        stubPage(List.of(log));
        stubMapper(log);

        AuditTrailPageResponse response = service.getByAction("DELETE", "journal_header", 0, 20);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).action()).isEqualTo("DELETE");
    }

    @Test
    void getJournalAuditHistory_journalNotFound_throws404() {
        UUID journalId = UUID.randomUUID();
        when(journalHeaderRepository.findById(journalId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getJournalAuditHistory(journalId))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "JOURNAL_NOT_FOUND");
    }

    @Test
    void getJournalAuditHistory_includesAllEvents() {
        UUID journalId = UUID.randomUUID();
        JournalHeader journal = JournalHeader.builder().id(journalId).build();
        when(journalHeaderRepository.findById(journalId)).thenReturn(Optional.of(journal));
        when(journalHeaderRepository.findByReversalOfId(journalId)).thenReturn(Optional.empty());

        AuditLog createLog = auditLog("journal_header", journalId, AuditAction.CREATE, "creator1");
        when(auditLogRepository.findByEntityNameAndEntityIdOrderByPerformedAtAsc("journal_header", journalId))
                .thenReturn(List.of(createLog));
        stubMapper(createLog);

        JournalApprovalLog approvalLog = JournalApprovalLog.builder()
                .id(UUID.randomUUID())
                .journalHeader(journal)
                .action("APPROVED")
                .performedBy("approver1")
                .fromStatus("PENDING_APPROVAL")
                .toStatus("APPROVED")
                .createdAt(Instant.now())
                .build();
        when(approvalLogRepository.findByJournalHeaderIdOrderByCreatedAtAsc(journalId))
                .thenReturn(List.of(approvalLog));

        List<AuditTrailResponse> history = service.getJournalAuditHistory(journalId);

        assertThat(history).hasSize(2);
        assertThat(history).extracting(AuditTrailResponse::entityType)
                .containsExactlyInAnyOrder("journal_header", "journal_approval_log");
    }

    @Test
    void getJournalAuditHistory_includesReversalCounterpartEvents() {
        UUID journalId = UUID.randomUUID();
        UUID reversalId = UUID.randomUUID();
        JournalHeader journal = JournalHeader.builder().id(journalId).build();
        JournalHeader reversal = JournalHeader.builder().id(reversalId).build();
        when(journalHeaderRepository.findById(journalId)).thenReturn(Optional.of(journal));
        when(journalHeaderRepository.findByReversalOfId(journalId)).thenReturn(Optional.of(reversal));

        AuditLog sourceLog = auditLog("journal_header", journalId, AuditAction.UPDATE, "reverser1");
        AuditLog reversalLog = auditLog("journal_header", reversalId, AuditAction.CREATE, "reverser1");
        when(auditLogRepository.findByEntityNameAndEntityIdOrderByPerformedAtAsc("journal_header", journalId))
                .thenReturn(List.of(sourceLog));
        when(auditLogRepository.findByEntityNameAndEntityIdOrderByPerformedAtAsc("journal_header", reversalId))
                .thenReturn(List.of(reversalLog));
        stubMapper(sourceLog);
        stubMapper(reversalLog);
        when(approvalLogRepository.findByJournalHeaderIdOrderByCreatedAtAsc(journalId)).thenReturn(List.of());

        List<AuditTrailResponse> history = service.getJournalAuditHistory(journalId);

        assertThat(history).hasSize(2);
        verify(auditLogRepository).findByEntityNameAndEntityIdOrderByPerformedAtAsc("journal_header", reversalId);
    }

    private void stubPage(List<AuditLog> logs) {
        lenient().when(auditLogRepository.findAll(
                        any(org.springframework.data.jpa.domain.Specification.class),
                        any(org.springframework.data.domain.Pageable.class)))
                .thenAnswer(inv -> new org.springframework.data.domain.PageImpl<>(logs, inv.getArgument(1), logs.size()));
    }

    private void stubMapper(AuditLog log) {
        lenient().when(mapper.toResponse(log)).thenReturn(AuditTrailResponse.builder()
                .id(log.getId())
                .entityType(log.getEntityName())
                .entityId(log.getEntityId())
                .action(log.getAction().name())
                .performedBy(log.getPerformedBy())
                .createdAt(log.getPerformedAt())
                .build());
    }

    private AuditLog auditLog(String entityName, UUID entityId, AuditAction action, String performedBy) {
        return AuditLog.builder()
                .id(UUID.randomUUID())
                .entityName(entityName)
                .entityId(entityId)
                .action(action)
                .performedBy(performedBy)
                .performedAt(Instant.now())
                .build();
    }
}
