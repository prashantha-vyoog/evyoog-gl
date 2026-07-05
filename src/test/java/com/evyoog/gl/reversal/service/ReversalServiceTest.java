package com.evyoog.gl.reversal.service;

import com.evyoog.gl.common.audit.service.AuditService;
import com.evyoog.gl.common.exception.EvyoogException;
import com.evyoog.gl.dimension.domain.DimensionValue;
import com.evyoog.gl.enterprise.domain.LegalEntity;
import com.evyoog.gl.ledger.domain.FinanceMode;
import com.evyoog.gl.ledger.domain.Ledger;
import com.evyoog.gl.period.domain.AccountingPeriod;
import com.evyoog.gl.posting.domain.JournalCategory;
import com.evyoog.gl.posting.domain.JournalHeader;
import com.evyoog.gl.posting.domain.JournalLine;
import com.evyoog.gl.posting.domain.JournalSource;
import com.evyoog.gl.posting.domain.JournalStatus;
import com.evyoog.gl.posting.dto.PostingRequest;
import com.evyoog.gl.posting.dto.PostingResult;
import com.evyoog.gl.posting.repository.JournalCategoryRepository;
import com.evyoog.gl.posting.repository.JournalHeaderRepository;
import com.evyoog.gl.posting.repository.JournalSourceRepository;
import com.evyoog.gl.posting.service.PostingEngine;
import com.evyoog.gl.reversal.dto.ReversalRequest;
import com.evyoog.gl.reversal.dto.ReversalResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReversalServiceTest {

    @Mock private JournalHeaderRepository journalHeaderRepository;
    @Mock private JournalSourceRepository journalSourceRepository;
    @Mock private JournalCategoryRepository journalCategoryRepository;
    @Mock private PostingEngine postingEngine;
    @Mock private AuditService auditService;

    private ReversalService service;

    private LegalEntity legalEntity;
    private JournalSource manualSource;
    private JournalSource reversalSource;
    private JournalCategory category;
    private JournalCategory reversalCategory;
    private DimensionValue account1;
    private DimensionValue account2;
    private AccountingPeriod period;
    private Ledger ledger;
    private UUID targetPeriodId;

    @BeforeEach
    void setUp() {
        service = new ReversalService(journalHeaderRepository, journalSourceRepository,
                journalCategoryRepository, postingEngine, auditService);

        legalEntity = LegalEntity.builder().id(UUID.randomUUID()).code("LE1").name("LE One").build();
        manualSource = JournalSource.builder().id(UUID.randomUUID()).code("MANUAL").name("Manual").requiresApproval(false).build();
        reversalSource = JournalSource.builder().id(UUID.randomUUID()).code("REVERSAL").name("Reversal").requiresApproval(false).build();
        category = JournalCategory.builder().id(UUID.randomUUID()).code("ADJUSTMENT").name("Adjustment").build();
        reversalCategory = JournalCategory.builder().id(UUID.randomUUID()).code("REVERSAL").name("Reversal").build();
        account1 = DimensionValue.builder().id(UUID.randomUUID()).code("1000").name("Cash").isPostable(true).isSummary(false).build();
        account2 = DimensionValue.builder().id(UUID.randomUUID()).code("4000").name("Revenue").isPostable(true).isSummary(false).build();
        period = AccountingPeriod.builder().id(UUID.randomUUID()).name("APR-2026").fiscalYear("2026-27").build();
        ledger = Ledger.builder().code("LDG").name("Primary").financeMode(FinanceMode.THICK).functionalCurrency("INR").build();
        ledger.setId(UUID.randomUUID());
        targetPeriodId = UUID.randomUUID();
    }

    private JournalHeader header(JournalStatus status, FinanceMode mode) {
        JournalHeader h = JournalHeader.builder()
                .journalNumber("JE-2618-00001")
                .legalEntity(legalEntity)
                .ledger(ledger)
                .accountingPeriod(period)
                .journalSource(manualSource)
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

    private JournalHeader reversalJournalRow() {
        JournalHeader h = JournalHeader.builder()
                .journalNumber("JE-2618-00099")
                .legalEntity(legalEntity)
                .ledger(ledger)
                .accountingPeriod(period)
                .journalSource(reversalSource)
                .journalCategory(reversalCategory)
                .glDate(LocalDate.now())
                .accountingDate(LocalDate.now())
                .currencyCode("INR")
                .exchangeRate(BigDecimal.ONE)
                .totalDebit(new BigDecimal("100.00"))
                .totalCredit(new BigDecimal("100.00"))
                .status(JournalStatus.POSTED)
                .financeModeSnapshot(FinanceMode.THICK)
                .build();
        h.setId(UUID.randomUUID());
        h.setLines(List.of());
        return h;
    }

    // ---- reverse() ----

    @Test
    void testReverse_postedThickJournal_createsReversalAndMarksSourceReversed() {
        JournalHeader source = header(JournalStatus.POSTED, FinanceMode.THICK);
        JournalHeader reversal = reversalJournalRow();

        when(journalHeaderRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(journalHeaderRepository.existsByReversalOfId(source.getId())).thenReturn(false);
        when(journalSourceRepository.findByCode("REVERSAL")).thenReturn(Optional.of(reversalSource));
        when(journalCategoryRepository.findByCode("REVERSAL")).thenReturn(Optional.of(reversalCategory));
        when(postingEngine.post(any())).thenReturn(PostingResult.posted(reversal.getId(), reversal.getJournalNumber(), FinanceMode.THICK));
        when(journalHeaderRepository.findById(reversal.getId())).thenReturn(Optional.of(reversal));
        when(journalHeaderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReversalResponse response = service.reverse(source.getId(),
                new ReversalRequest(targetPeriodId, "reverser1", "Wrong period"));

        assertThat(response.originalJournalId()).isEqualTo(source.getId());
        assertThat(response.originalStatus()).isEqualTo(JournalStatus.REVERSED);
        assertThat(response.reversalJournalId()).isEqualTo(reversal.getId());
        assertThat(response.reversalStatus()).isEqualTo(JournalStatus.POSTED);
        assertThat(source.getStatus()).isEqualTo(JournalStatus.REVERSED);
        assertThat(reversal.getIsReversal()).isTrue();
        assertThat(reversal.getReversalOf()).isEqualTo(source);
    }

    @Test
    void testReverse_notPosted_throws409() {
        JournalHeader source = header(JournalStatus.DRAFT, FinanceMode.THICK);
        when(journalHeaderRepository.findById(source.getId())).thenReturn(Optional.of(source));

        assertThatThrownBy(() -> service.reverse(source.getId(), new ReversalRequest(targetPeriodId, "reverser1", null)))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_REVERSAL_STATE");

        verify(postingEngine, never()).post(any());
    }

    @Test
    void testReverse_thinMode_throws409() {
        JournalHeader source = header(JournalStatus.POSTED, FinanceMode.THIN);
        when(journalHeaderRepository.findById(source.getId())).thenReturn(Optional.of(source));

        assertThatThrownBy(() -> service.reverse(source.getId(), new ReversalRequest(targetPeriodId, "reverser1", null)))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "THICK_MODE_ONLY");

        verify(postingEngine, never()).post(any());
    }

    @Test
    void testReverse_alreadyReversed_throws409() {
        JournalHeader source = header(JournalStatus.POSTED, FinanceMode.THICK);
        when(journalHeaderRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(journalHeaderRepository.existsByReversalOfId(source.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.reverse(source.getId(), new ReversalRequest(targetPeriodId, "reverser1", null)))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "ALREADY_REVERSED");

        verify(postingEngine, never()).post(any());
    }

    @Test
    void testReverse_targetPeriodNotOpen_propagatesFromPostingEngine() {
        JournalHeader source = header(JournalStatus.POSTED, FinanceMode.THICK);
        when(journalHeaderRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(journalHeaderRepository.existsByReversalOfId(source.getId())).thenReturn(false);
        when(journalSourceRepository.findByCode("REVERSAL")).thenReturn(Optional.of(reversalSource));
        when(journalCategoryRepository.findByCode("REVERSAL")).thenReturn(Optional.of(reversalCategory));
        when(postingEngine.post(any())).thenThrow(new EvyoogException("PERIOD_NOT_OPEN", "Period is CLOSED."));

        assertThatThrownBy(() -> service.reverse(source.getId(), new ReversalRequest(targetPeriodId, "reverser1", null)))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "PERIOD_NOT_OPEN");
    }

    @Test
    void testReverse_journalNotFound_throws404() {
        UUID missingId = UUID.randomUUID();
        when(journalHeaderRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reverse(missingId, new ReversalRequest(targetPeriodId, "reverser1", null)))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "JOURNAL_NOT_FOUND");
    }

    @Test
    void testReverse_linesFlipped_drBecomesCrAndCrBecomesDr() {
        JournalHeader source = header(JournalStatus.POSTED, FinanceMode.THICK);
        JournalHeader reversal = reversalJournalRow();

        when(journalHeaderRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(journalHeaderRepository.existsByReversalOfId(source.getId())).thenReturn(false);
        when(journalSourceRepository.findByCode("REVERSAL")).thenReturn(Optional.of(reversalSource));
        when(journalCategoryRepository.findByCode("REVERSAL")).thenReturn(Optional.of(reversalCategory));
        when(postingEngine.post(any())).thenReturn(PostingResult.posted(reversal.getId(), reversal.getJournalNumber(), FinanceMode.THICK));
        when(journalHeaderRepository.findById(reversal.getId())).thenReturn(Optional.of(reversal));
        when(journalHeaderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.reverse(source.getId(), new ReversalRequest(targetPeriodId, "reverser1", null));

        ArgumentCaptor<PostingRequest> captor = ArgumentCaptor.forClass(PostingRequest.class);
        verify(postingEngine).post(captor.capture());
        PostingRequest posted = captor.getValue();

        assertThat(posted.getAccountingPeriodId()).isEqualTo(targetPeriodId);
        assertThat(posted.getJournalSourceId()).isEqualTo(reversalSource.getId());
        assertThat(posted.getJournalCategoryId()).isEqualTo(reversalCategory.getId());
        assertThat(posted.getLines()).hasSize(2);
        assertThat(posted.getLines().get(0).getDebitAmount()).isNull();
        assertThat(posted.getLines().get(0).getCreditAmount()).isEqualByComparingTo("100.00");
        assertThat(posted.getLines().get(1).getDebitAmount()).isEqualByComparingTo("100.00");
        assertThat(posted.getLines().get(1).getCreditAmount()).isNull();
    }

    // ---- getReversalDetails() ----

    @Test
    void testGetReversalDetails_returnsCorrectPair() {
        JournalHeader source = header(JournalStatus.REVERSED, FinanceMode.THICK);
        JournalHeader reversal = reversalJournalRow();
        reversal.setReversalOf(source);
        reversal.setIsReversal(true);

        when(journalHeaderRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(journalHeaderRepository.findByReversalOfId(source.getId())).thenReturn(Optional.of(reversal));

        ReversalResponse response = service.getReversalDetails(source.getId());

        assertThat(response.originalJournalId()).isEqualTo(source.getId());
        assertThat(response.reversalJournalId()).isEqualTo(reversal.getId());
        assertThat(response.originalStatus()).isEqualTo(JournalStatus.REVERSED);
        assertThat(response.reversalStatus()).isEqualTo(JournalStatus.POSTED);
    }

    @Test
    void testGetReversalDetails_noReversal_throws404() {
        JournalHeader source = header(JournalStatus.POSTED, FinanceMode.THICK);
        when(journalHeaderRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(journalHeaderRepository.findByReversalOfId(source.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getReversalDetails(source.getId()))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "REVERSAL_NOT_FOUND");
    }
}
