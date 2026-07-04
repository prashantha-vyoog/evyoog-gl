package com.evyoog.gl.journal.service;

import com.evyoog.gl.calendar.domain.AccountingCalendar;
import com.evyoog.gl.calendar.domain.PeriodType;
import com.evyoog.gl.calendar.repository.AccountingCalendarRepository;
import com.evyoog.gl.common.audit.service.AuditService;
import com.evyoog.gl.common.exception.EvyoogException;
import com.evyoog.gl.dimension.domain.DimensionValue;
import com.evyoog.gl.dimension.repository.DimensionValueRepository;
import com.evyoog.gl.enterprise.domain.AccountingStandard;
import com.evyoog.gl.enterprise.domain.LegalEntity;
import com.evyoog.gl.enterprise.repository.LegalEntityRepository;
import com.evyoog.gl.journal.dto.CreateJournalLineRequest;
import com.evyoog.gl.journal.dto.CreateJournalRequest;
import com.evyoog.gl.journal.dto.JournalResponse;
import com.evyoog.gl.journal.dto.UpdateJournalRequest;
import com.evyoog.gl.journal.mapper.JournalMapper;
import com.evyoog.gl.ledger.domain.FinanceMode;
import com.evyoog.gl.ledger.domain.Ledger;
import com.evyoog.gl.ledger.repository.LegalEntityLedgerRepository;
import com.evyoog.gl.period.domain.AccountingPeriod;
import com.evyoog.gl.period.service.AccountingPeriodService;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JournalServiceTest {

    @Mock private JournalHeaderRepository journalHeaderRepository;
    @Mock private JournalSourceRepository journalSourceRepository;
    @Mock private JournalCategoryRepository journalCategoryRepository;
    @Mock private LegalEntityRepository legalEntityRepository;
    @Mock private LegalEntityLedgerRepository legalEntityLedgerRepository;
    @Mock private AccountingCalendarRepository accountingCalendarRepository;
    @Mock private AccountingPeriodService accountingPeriodService;
    @Mock private DimensionValueRepository dimensionValueRepository;
    @Mock private PostingEngine postingEngine;
    @Mock private AuditService auditService;
    @Mock private JournalMapper mapper;
    @InjectMocks private JournalService service;

    private UUID legalEntityId;
    private UUID ledgerId;
    private UUID calendarId;
    private UUID periodId;
    private UUID categoryId;
    private UUID sourceIdNoApproval;
    private UUID sourceIdApproval;
    private UUID account1Id;
    private UUID account2Id;

    private Ledger ledger;
    private LegalEntity legalEntity;
    private JournalSource noApprovalSource;
    private JournalSource approvalSource;
    private JournalCategory category;
    private DimensionValue account1;
    private DimensionValue account2;

    @BeforeEach
    void setUp() {
        legalEntityId = UUID.randomUUID();
        ledgerId = UUID.randomUUID();
        calendarId = UUID.randomUUID();
        periodId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        sourceIdNoApproval = UUID.randomUUID();
        sourceIdApproval = UUID.randomUUID();
        account1Id = UUID.randomUUID();
        account2Id = UUID.randomUUID();

        ledger = Ledger.builder().code("LDG").name("Primary Ledger")
                .financeMode(FinanceMode.THICK).functionalCurrency("INR").build();
        ledger.setId(ledgerId);

        AccountingCalendar calendar = AccountingCalendar.builder().ledger(ledger).name("FY Calendar")
                .fiscalYearStartMonth(4).fiscalYearStartDay(1).periodType(PeriodType.MONTHLY).periodsPerYear(12).build();
        calendar.setId(calendarId);

        AccountingPeriod period = AccountingPeriod.builder().name("APR-2026").fiscalYear("2026-27")
                .periodNumber(1).quarterNumber(1)
                .startDate(LocalDate.of(2026, 4, 1)).endDate(LocalDate.of(2026, 4, 30)).build();
        period.setId(periodId);

        noApprovalSource = JournalSource.builder().id(sourceIdNoApproval).code("MANUAL")
                .name("Manual Journal Entry").requiresApproval(false).build();
        approvalSource = JournalSource.builder().id(sourceIdApproval).code("PAYROLL")
                .name("Payroll").requiresApproval(true).build();

        category = JournalCategory.builder().id(categoryId).code("ADJUSTMENT").name("Adjustment").build();

        legalEntity = LegalEntity.builder().code("LE1").name("LE One").accountingStandard(AccountingStandard.IND_AS).build();
        legalEntity.setId(legalEntityId);

        account1 = DimensionValue.builder().id(account1Id).code("1000").name("Cash").isPostable(true).isSummary(false).build();
        account2 = DimensionValue.builder().id(account2Id).code("4000").name("Revenue").isPostable(true).isSummary(false).build();

        lenient().when(legalEntityLedgerRepository.findPrimaryLedgerByLegalEntityId(legalEntityId))
                .thenReturn(Optional.of(ledger));
        lenient().when(accountingCalendarRepository.findByLedgerId(ledgerId)).thenReturn(Optional.of(calendar));
        lenient().when(accountingPeriodService.findPeriodByDate(eq(calendarId), any())).thenReturn(period);
        lenient().when(journalSourceRepository.findById(sourceIdNoApproval)).thenReturn(Optional.of(noApprovalSource));
        lenient().when(journalSourceRepository.findById(sourceIdApproval)).thenReturn(Optional.of(approvalSource));
        lenient().when(journalCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        lenient().when(legalEntityRepository.findById(legalEntityId)).thenReturn(Optional.of(legalEntity));
        lenient().when(dimensionValueRepository.findById(account1Id)).thenReturn(Optional.of(account1));
        lenient().when(dimensionValueRepository.findById(account2Id)).thenReturn(Optional.of(account2));
        lenient().when(journalHeaderRepository.countByCreatedAtAfter(any())).thenReturn(0L);
        lenient().when(journalHeaderRepository.save(any(JournalHeader.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(mapper.toResponse(any())).thenAnswer(inv -> toResponseStub(inv.getArgument(0)));
    }

    private JournalResponse toResponseStub(JournalHeader h) {
        return new JournalResponse(
                h.getId(), h.getJournalNumber(),
                h.getLegalEntity() != null ? h.getLegalEntity().getId() : null,
                h.getLegalEntity() != null ? h.getLegalEntity().getName() : null,
                h.getLedger() != null ? h.getLedger().getId() : null,
                h.getLedger() != null ? h.getLedger().getName() : null,
                h.getLedger() != null && h.getLedger().getFinanceMode() != null ? h.getLedger().getFinanceMode().name() : null,
                h.getAccountingPeriod() != null ? h.getAccountingPeriod().getId() : null,
                h.getAccountingPeriod() != null ? h.getAccountingPeriod().getName() : null,
                h.getAccountingPeriod() != null ? h.getAccountingPeriod().getFiscalYear() : null,
                h.getJournalSource() != null ? h.getJournalSource().getId() : null,
                h.getJournalSource() != null ? h.getJournalSource().getCode() : null,
                h.getJournalSource() != null ? h.getJournalSource().getName() : null,
                h.getJournalCategory() != null ? h.getJournalCategory().getId() : null,
                h.getJournalCategory() != null ? h.getJournalCategory().getCode() : null,
                h.getJournalCategory() != null ? h.getJournalCategory().getName() : null,
                h.getDescription(), h.getGlDate(), h.getAccountingDate(), h.getCurrencyCode(), h.getExchangeRate(),
                h.getTotalDebit(), h.getTotalCredit(), h.getStatus(),
                h.getFinanceModeSnapshot() != null ? h.getFinanceModeSnapshot().name() : null,
                h.getPostedAt(), h.getPostedBy(), h.getIsReversal(), h.getExternalReference(), h.getNotes(),
                List.of(), h.getCreatedAt(), h.getCreatedBy());
    }

    private CreateJournalRequest requestWithLines(UUID sourceId, LocalDate glDate, LocalDate accountingDate,
                                                    List<CreateJournalLineRequest> lines) {
        return new CreateJournalRequest(legalEntityId, sourceId, categoryId, "Test journal",
                glDate, accountingDate, "INR", BigDecimal.ONE, null, null, lines);
    }

    private List<CreateJournalLineRequest> balancedLines() {
        return List.of(
                new CreateJournalLineRequest(Map.of(), account1Id, new BigDecimal("100.00"), null,
                        "INR", null, false, null, false, null),
                new CreateJournalLineRequest(Map.of(), account2Id, null, new BigDecimal("100.00"),
                        "INR", null, false, null, false, null));
    }

    private JournalHeader headerWithStatus(JournalStatus status, JournalSource source) {
        JournalHeader header = JournalHeader.builder()
                .journalNumber("JE-2618-00001")
                .legalEntity(legalEntity)
                .ledger(ledger)
                .journalSource(source)
                .journalCategory(category)
                .description("Test journal")
                .glDate(LocalDate.of(2026, 4, 10))
                .accountingDate(LocalDate.of(2026, 4, 10))
                .currencyCode("INR")
                .exchangeRate(BigDecimal.ONE)
                .totalDebit(new BigDecimal("100.00"))
                .totalCredit(new BigDecimal("100.00"))
                .status(status)
                .financeModeSnapshot(FinanceMode.THICK)
                .build();
        header.setId(UUID.randomUUID());

        AccountingPeriod period = AccountingPeriod.builder().name("APR-2026").fiscalYear("2026-27")
                .periodNumber(1).quarterNumber(1)
                .startDate(LocalDate.of(2026, 4, 1)).endDate(LocalDate.of(2026, 4, 30)).build();
        period.setId(periodId);
        header.setAccountingPeriod(period);

        JournalLine line1 = JournalLine.builder().journalHeader(header).lineNumber(1)
                .accountCombination(Map.of()).naturalAccount(account1).debitAmount(new BigDecimal("100.00"))
                .currencyCode("INR").build();
        JournalLine line2 = JournalLine.builder().journalHeader(header).lineNumber(2)
                .accountCombination(Map.of()).naturalAccount(account2).creditAmount(new BigDecimal("100.00"))
                .currencyCode("INR").build();
        header.setLines(List.of(line1, line2));
        return header;
    }

    // ---- create() ----

    @Test
    void testCreate_noApprovalRequired_postsDirectly() {
        UUID postedId = UUID.randomUUID();
        JournalHeader postedHeader = headerWithStatus(JournalStatus.POSTED, noApprovalSource);
        when(postingEngine.post(any())).thenReturn(PostingResult.posted(postedId, "JE-2618-00001", FinanceMode.THICK));
        when(journalHeaderRepository.findById(postedId)).thenReturn(Optional.of(postedHeader));

        JournalResponse response = service.create(
                requestWithLines(sourceIdNoApproval, LocalDate.of(2026, 4, 10), null, balancedLines()), "tester");

        assertThat(response.status()).isEqualTo(JournalStatus.POSTED);
        verify(postingEngine).post(any());
        verify(journalHeaderRepository, never()).save(any());
    }

    @Test
    void testCreate_approvalRequired_savesDraft() {
        JournalResponse response = service.create(
                requestWithLines(sourceIdApproval, LocalDate.of(2026, 4, 10), null, balancedLines()), "tester");

        assertThat(response.status()).isEqualTo(JournalStatus.DRAFT);
        verify(postingEngine, never()).post(any());
        verify(journalHeaderRepository).save(any(JournalHeader.class));
    }

    @Test
    void testCreate_singleLine_throwsMINIMUM_TWO_LINES() {
        List<CreateJournalLineRequest> oneLine = List.of(
                new CreateJournalLineRequest(Map.of(), account1Id, new BigDecimal("100.00"), null,
                        "INR", null, false, null, false, null));

        assertThatThrownBy(() -> service.create(
                requestWithLines(sourceIdNoApproval, LocalDate.now(), null, oneLine), "tester"))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "MINIMUM_TWO_LINES");
    }

    @Test
    void testCreate_bothDebitAndCredit_throwsINVALID_LINE_AMOUNTS() {
        List<CreateJournalLineRequest> lines = List.of(
                new CreateJournalLineRequest(Map.of(), account1Id, new BigDecimal("100.00"), new BigDecimal("100.00"),
                        "INR", null, false, null, false, null),
                new CreateJournalLineRequest(Map.of(), account2Id, null, new BigDecimal("100.00"),
                        "INR", null, false, null, false, null));

        assertThatThrownBy(() -> service.create(
                requestWithLines(sourceIdNoApproval, LocalDate.now(), null, lines), "tester"))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_LINE_AMOUNTS");
    }

    @Test
    void testCreate_neitherDebitNorCredit_throwsINVALID_LINE_AMOUNTS() {
        List<CreateJournalLineRequest> lines = List.of(
                new CreateJournalLineRequest(Map.of(), account1Id, null, null,
                        "INR", null, false, null, false, null),
                new CreateJournalLineRequest(Map.of(), account2Id, null, new BigDecimal("100.00"),
                        "INR", null, false, null, false, null));

        assertThatThrownBy(() -> service.create(
                requestWithLines(sourceIdNoApproval, LocalDate.now(), null, lines), "tester"))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_LINE_AMOUNTS");
    }

    @Test
    void testCreate_accountingDateDefaultsToGlDate() {
        UUID postedId = UUID.randomUUID();
        when(postingEngine.post(any())).thenReturn(PostingResult.posted(postedId, "JE-2618-00001", FinanceMode.THICK));
        when(journalHeaderRepository.findById(postedId)).thenReturn(Optional.of(headerWithStatus(JournalStatus.POSTED, noApprovalSource)));

        LocalDate glDate = LocalDate.of(2026, 5, 20);
        service.create(requestWithLines(sourceIdNoApproval, glDate, null, balancedLines()), "tester");

        ArgumentCaptor<PostingRequest> captor = ArgumentCaptor.forClass(PostingRequest.class);
        verify(postingEngine).post(captor.capture());
        assertThat(captor.getValue().getAccountingDate()).isEqualTo(glDate);
    }

    // ---- submit() ----

    @Test
    void testSubmit_draft_noApproval_postsDirectly() {
        JournalHeader draft = headerWithStatus(JournalStatus.DRAFT, noApprovalSource);
        UUID postedId = UUID.randomUUID();
        when(journalHeaderRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(postingEngine.post(any())).thenReturn(PostingResult.posted(postedId, "JE-2618-00002", FinanceMode.THICK));
        when(journalHeaderRepository.findById(postedId)).thenReturn(Optional.of(headerWithStatus(JournalStatus.POSTED, noApprovalSource)));

        JournalResponse response = service.submit(draft.getId(), "tester");

        assertThat(response.status()).isEqualTo(JournalStatus.POSTED);
        verify(postingEngine).post(any());
    }

    @Test
    void testSubmit_draft_requiresApproval_toPendingApproval() {
        JournalHeader draft = headerWithStatus(JournalStatus.DRAFT, approvalSource);
        when(journalHeaderRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

        JournalResponse response = service.submit(draft.getId(), "tester");

        assertThat(response.status()).isEqualTo(JournalStatus.PENDING_APPROVAL);
        verify(postingEngine, never()).post(any());
        verify(journalHeaderRepository).save(any(JournalHeader.class));
    }

    @Test
    void testSubmit_notDraft_throws() {
        JournalHeader posted = headerWithStatus(JournalStatus.POSTED, noApprovalSource);
        when(journalHeaderRepository.findById(posted.getId())).thenReturn(Optional.of(posted));

        assertThatThrownBy(() -> service.submit(posted.getId(), "tester"))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "JOURNAL_NOT_SUBMITTABLE");
    }

    // ---- post() ----

    @Test
    void testPost_approved_postsSuccessfully() {
        JournalHeader approved = headerWithStatus(JournalStatus.APPROVED, approvalSource);
        UUID postedId = UUID.randomUUID();
        when(journalHeaderRepository.findById(approved.getId())).thenReturn(Optional.of(approved));
        when(postingEngine.post(any())).thenReturn(PostingResult.posted(postedId, "JE-2618-00003", FinanceMode.THICK));
        when(journalHeaderRepository.findById(postedId)).thenReturn(Optional.of(headerWithStatus(JournalStatus.POSTED, approvalSource)));

        JournalResponse response = service.post(approved.getId(), "tester");

        assertThat(response.status()).isEqualTo(JournalStatus.POSTED);
        ArgumentCaptor<PostingRequest> captor = ArgumentCaptor.forClass(PostingRequest.class);
        verify(postingEngine).post(captor.capture());
        assertThat(captor.getValue().getInitialStatus()).isEqualTo(JournalStatus.APPROVED);
    }

    @Test
    void testPost_notApproved_throws() {
        JournalHeader draft = headerWithStatus(JournalStatus.DRAFT, approvalSource);
        when(journalHeaderRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.post(draft.getId(), "tester"))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "JOURNAL_NOT_APPROVABLE");
    }

    // ---- cancel() ----

    @Test
    void testCancel_draft_cancelled() {
        JournalHeader draft = headerWithStatus(JournalStatus.DRAFT, noApprovalSource);
        when(journalHeaderRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

        JournalResponse response = service.cancel(draft.getId(), "tester");

        assertThat(response.status()).isEqualTo(JournalStatus.CANCELLED);
    }

    @Test
    void testCancel_posted_throws() {
        JournalHeader posted = headerWithStatus(JournalStatus.POSTED, noApprovalSource);
        when(journalHeaderRepository.findById(posted.getId())).thenReturn(Optional.of(posted));

        assertThatThrownBy(() -> service.cancel(posted.getId(), "tester"))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "JOURNAL_NOT_CANCELLABLE");
    }

    // ---- update() ----

    @Test
    void testUpdate_draft_updatesDescription() {
        JournalHeader draft = headerWithStatus(JournalStatus.DRAFT, noApprovalSource);
        when(journalHeaderRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

        JournalResponse response = service.update(draft.getId(), new UpdateJournalRequest("Updated description", null), "tester");

        assertThat(response.description()).isEqualTo("Updated description");
    }

    @Test
    void testUpdate_posted_throws() {
        JournalHeader posted = headerWithStatus(JournalStatus.POSTED, noApprovalSource);
        when(journalHeaderRepository.findById(posted.getId())).thenReturn(Optional.of(posted));

        assertThatThrownBy(() -> service.update(posted.getId(), new UpdateJournalRequest("x", null), "tester"))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "JOURNAL_NOT_EDITABLE");
    }
}
