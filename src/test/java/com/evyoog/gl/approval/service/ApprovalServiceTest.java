package com.evyoog.gl.approval.service;

import com.evyoog.gl.approval.domain.JournalApprovalLog;
import com.evyoog.gl.approval.dto.ApprovalActionRequest;
import com.evyoog.gl.approval.dto.ApprovalResponse;
import com.evyoog.gl.approval.dto.JournalApprovalLogResponse;
import com.evyoog.gl.approval.repository.JournalApprovalLogRepository;
import com.evyoog.gl.common.audit.service.AuditService;
import com.evyoog.gl.common.exception.EvyoogException;
import com.evyoog.gl.dimension.domain.DimensionValue;
import com.evyoog.gl.enterprise.domain.LegalEntity;
import com.evyoog.gl.journal.dto.JournalSummaryResponse;
import com.evyoog.gl.journal.mapper.JournalMapper;
import com.evyoog.gl.ledger.domain.FinanceMode;
import com.evyoog.gl.ledger.domain.Ledger;
import com.evyoog.gl.period.domain.AccountingPeriod;
import com.evyoog.gl.posting.domain.JournalCategory;
import com.evyoog.gl.posting.domain.JournalHeader;
import com.evyoog.gl.posting.domain.JournalLine;
import com.evyoog.gl.posting.domain.JournalSource;
import com.evyoog.gl.posting.domain.JournalStatus;
import com.evyoog.gl.posting.dto.PostingResult;
import com.evyoog.gl.posting.repository.JournalHeaderRepository;
import com.evyoog.gl.posting.service.PostingEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    @Mock private JournalHeaderRepository journalHeaderRepository;
    @Mock private JournalApprovalLogRepository approvalLogRepository;
    @Mock private PostingEngine postingEngine;
    @Mock private JournalMapper journalMapper;
    @Mock private AuditService auditService;

    private ApprovalService service;

    private UUID legalEntityId;
    private LegalEntity legalEntity;
    private JournalSource source;
    private JournalCategory category;
    private DimensionValue account1;
    private DimensionValue account2;
    private AccountingPeriod period;
    private Ledger ledger;

    @BeforeEach
    void setUp() {
        service = new ApprovalService(journalHeaderRepository, approvalLogRepository, postingEngine, journalMapper, auditService);

        legalEntityId = UUID.randomUUID();
        legalEntity = LegalEntity.builder().id(legalEntityId).code("LE1").name("LE One").build();
        source = JournalSource.builder().id(UUID.randomUUID()).code("MANUAL").name("Manual").requiresApproval(true).build();
        category = JournalCategory.builder().id(UUID.randomUUID()).code("ADJ").name("Adjustment").build();
        account1 = DimensionValue.builder().id(UUID.randomUUID()).code("1000").name("Cash").isPostable(true).isSummary(false).build();
        account2 = DimensionValue.builder().id(UUID.randomUUID()).code("4000").name("Revenue").isPostable(true).isSummary(false).build();
        period = AccountingPeriod.builder().id(UUID.randomUUID()).name("APR-2026").fiscalYear("2026-27").build();
        ledger = Ledger.builder().code("LDG").name("Primary").financeMode(FinanceMode.THICK).functionalCurrency("INR").build();
        ledger.setId(UUID.randomUUID());
    }

    private JournalHeader header(JournalStatus status, FinanceMode mode) {
        JournalHeader h = JournalHeader.builder()
                .journalNumber("JE-2618-00001")
                .legalEntity(legalEntity)
                .ledger(ledger)
                .accountingPeriod(period)
                .journalSource(source)
                .journalCategory(category)
                .glDate(LocalDate.of(2026, 4, 10))
                .accountingDate(LocalDate.of(2026, 4, 10))
                .currencyCode("INR")
                .exchangeRate(BigDecimal.ONE)
                .totalDebit(new BigDecimal("100.00"))
                .totalCredit(new BigDecimal("100.00"))
                .status(status)
                .financeModeSnapshot(mode)
                .build();
        h.setId(UUID.randomUUID());

        JournalLine l1 = JournalLine.builder().journalHeader(h).lineNumber(1)
                .accountCombination(Map.of()).naturalAccount(account1).debitAmount(new BigDecimal("100.00"))
                .currencyCode("INR").build();
        JournalLine l2 = JournalLine.builder().journalHeader(h).lineNumber(2)
                .accountCombination(Map.of()).naturalAccount(account2).creditAmount(new BigDecimal("100.00"))
                .currencyCode("INR").build();
        h.setLines(List.of(l1, l2));
        return h;
    }

    // ---- approve() ----

    @Test
    void testApprove_pendingApproval_postsJournal() {
        JournalHeader journal = header(JournalStatus.PENDING_APPROVAL, FinanceMode.THICK);
        when(journalHeaderRepository.findById(journal.getId())).thenReturn(Optional.of(journal));
        when(journalHeaderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(postingEngine.post(any())).thenReturn(PostingResult.posted(UUID.randomUUID(), "JE-2618-00099", FinanceMode.THICK));
        lenient().when(approvalLogRepository.findByJournalHeaderIdOrderByCreatedAtAsc(journal.getId())).thenReturn(List.of());

        ApprovalResponse response = service.approve(journal.getId(), new ApprovalActionRequest("approver1", "Looks good"));

        assertThat(response.newStatus()).isEqualTo(JournalStatus.POSTED);
        assertThat(response.journalHeaderId()).isEqualTo(journal.getId());
        verify(postingEngine).post(any());
        verify(approvalLogRepository, times(2)).save(any(JournalApprovalLog.class));
    }

    @Test
    void testApprove_notPendingApproval_throws409() {
        JournalHeader journal = header(JournalStatus.DRAFT, FinanceMode.THICK);
        when(journalHeaderRepository.findById(journal.getId())).thenReturn(Optional.of(journal));

        assertThatThrownBy(() -> service.approve(journal.getId(), new ApprovalActionRequest("approver1", null)))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_APPROVAL_STATE");
    }

    @Test
    void testApprove_thinMode_throws409() {
        JournalHeader journal = header(JournalStatus.PENDING_APPROVAL, FinanceMode.THIN);
        when(journalHeaderRepository.findById(journal.getId())).thenReturn(Optional.of(journal));

        assertThatThrownBy(() -> service.approve(journal.getId(), new ApprovalActionRequest("approver1", null)))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "THICK_MODE_ONLY");

        verify(postingEngine, never()).post(any());
    }

    // ---- reject() ----

    @Test
    void testReject_pendingApproval_returnsToDraft() {
        JournalHeader journal = header(JournalStatus.PENDING_APPROVAL, FinanceMode.THICK);
        when(journalHeaderRepository.findById(journal.getId())).thenReturn(Optional.of(journal));
        when(journalHeaderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(approvalLogRepository.findByJournalHeaderIdOrderByCreatedAtAsc(journal.getId())).thenReturn(List.of());

        ApprovalResponse response = service.reject(journal.getId(), new ApprovalActionRequest("approver1", "Incorrect account"));

        assertThat(response.newStatus()).isEqualTo(JournalStatus.DRAFT);
        assertThat(journal.getStatus()).isEqualTo(JournalStatus.DRAFT);
        verify(postingEngine, never()).post(any());
    }

    @Test
    void testReject_notPendingApproval_throws409() {
        JournalHeader journal = header(JournalStatus.DRAFT, FinanceMode.THICK);
        when(journalHeaderRepository.findById(journal.getId())).thenReturn(Optional.of(journal));

        assertThatThrownBy(() -> service.reject(journal.getId(), new ApprovalActionRequest("approver1", null)))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_APPROVAL_STATE");
    }

    // ---- recall() ----

    @Test
    void testRecall_pendingApproval_returnsToDraft() {
        JournalHeader journal = header(JournalStatus.PENDING_APPROVAL, FinanceMode.THICK);
        when(journalHeaderRepository.findById(journal.getId())).thenReturn(Optional.of(journal));
        when(journalHeaderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(approvalLogRepository.findByJournalHeaderIdOrderByCreatedAtAsc(journal.getId())).thenReturn(List.of());

        ApprovalResponse response = service.recall(journal.getId(), new ApprovalActionRequest("submitter1", "Made a mistake"));

        assertThat(response.newStatus()).isEqualTo(JournalStatus.DRAFT);
        assertThat(journal.getStatus()).isEqualTo(JournalStatus.DRAFT);
    }

    @Test
    void testRecall_notPendingApproval_throws409() {
        JournalHeader journal = header(JournalStatus.POSTED, FinanceMode.THICK);
        when(journalHeaderRepository.findById(journal.getId())).thenReturn(Optional.of(journal));

        assertThatThrownBy(() -> service.recall(journal.getId(), new ApprovalActionRequest("submitter1", null)))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_APPROVAL_STATE");
    }

    // ---- getApprovalHistory() ----

    @Test
    void testGetApprovalHistory_returnsChronologicalLog() {
        JournalHeader journal = header(JournalStatus.APPROVED, FinanceMode.THICK);
        JournalApprovalLog log1 = JournalApprovalLog.builder().id(UUID.randomUUID()).journalHeader(journal)
                .action("APPROVED").performedBy("approver1").fromStatus("PENDING_APPROVAL").toStatus("APPROVED").build();
        when(approvalLogRepository.findByJournalHeaderIdOrderByCreatedAtAsc(journal.getId())).thenReturn(List.of(log1));

        List<JournalApprovalLogResponse> history = service.getApprovalHistory(journal.getId());

        assertThat(history).hasSize(1);
        assertThat(history.get(0).action()).isEqualTo("APPROVED");
        assertThat(history.get(0).fromStatus()).isEqualTo("PENDING_APPROVAL");
        assertThat(history.get(0).toStatus()).isEqualTo("APPROVED");
    }

    // ---- getPendingApprovals() ----

    @Test
    void testGetPendingApprovals_filtersCorrectly() {
        JournalHeader journal = header(JournalStatus.PENDING_APPROVAL, FinanceMode.THICK);
        when(journalHeaderRepository.findByLegalEntityIdAndStatus(legalEntityId, JournalStatus.PENDING_APPROVAL))
                .thenReturn(List.of(journal));
        when(journalMapper.toSummary(journal)).thenReturn(new JournalSummaryResponse(
                journal.getId(), journal.getJournalNumber(), "LE One", "Primary", "APR-2026",
                "MANUAL", "ADJ", null, journal.getGlDate(), journal.getTotalDebit(), journal.getTotalCredit(),
                JournalStatus.PENDING_APPROVAL, "THICK", null, journal.getCreatedAt()));

        List<JournalSummaryResponse> pending = service.getPendingApprovals(legalEntityId);

        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).status()).isEqualTo(JournalStatus.PENDING_APPROVAL);
    }
}
