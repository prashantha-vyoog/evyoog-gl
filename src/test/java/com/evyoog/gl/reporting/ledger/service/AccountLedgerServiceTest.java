package com.evyoog.gl.reporting.ledger.service;

import com.evyoog.gl.dimension.domain.AccountQualifier;
import com.evyoog.gl.dimension.domain.DimensionValue;
import com.evyoog.gl.dimension.domain.NormalBalance;
import com.evyoog.gl.dimension.repository.DimensionValueRepository;
import com.evyoog.gl.enterprise.domain.LegalEntity;
import com.evyoog.gl.enterprise.repository.LegalEntityRepository;
import com.evyoog.gl.period.domain.AccountingPeriod;
import com.evyoog.gl.period.repository.AccountingPeriodRepository;
import com.evyoog.gl.posting.domain.AccountBalance;
import com.evyoog.gl.posting.domain.JournalCategory;
import com.evyoog.gl.posting.domain.JournalHeader;
import com.evyoog.gl.posting.domain.JournalLine;
import com.evyoog.gl.posting.domain.JournalSource;
import com.evyoog.gl.posting.domain.JournalStatus;
import com.evyoog.gl.posting.repository.AccountBalanceRepository;
import com.evyoog.gl.posting.repository.JournalHeaderRepository;
import com.evyoog.gl.posting.repository.JournalLineRepository;
import com.evyoog.gl.reporting.ledger.dto.AccountLedgerResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountLedgerServiceTest {

    @Mock private AccountBalanceRepository accountBalanceRepository;
    @Mock private JournalLineRepository journalLineRepository;
    @Mock private DimensionValueRepository dimensionValueRepository;
    @Mock private AccountingPeriodRepository accountingPeriodRepository;
    @Mock private LegalEntityRepository legalEntityRepository;
    @Mock private JournalHeaderRepository journalHeaderRepository;

    private AccountLedgerService service;

    private UUID legalEntityId;
    private UUID periodId;
    private UUID accountId;
    private LegalEntity legalEntity;
    private AccountingPeriod period;
    private DimensionValue account;
    private JournalSource source;
    private JournalCategory category;

    @BeforeEach
    void setUp() {
        service = new AccountLedgerService(accountBalanceRepository, journalLineRepository,
                dimensionValueRepository, accountingPeriodRepository, legalEntityRepository,
                journalHeaderRepository);

        legalEntityId = UUID.randomUUID();
        periodId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        legalEntity = LegalEntity.builder().id(legalEntityId).code("LE1").name("Legal Entity 1").build();
        period = AccountingPeriod.builder().id(periodId).name("APR-2025").fiscalYear("2025-26").build();
        account = DimensionValue.builder().id(accountId).code("1000").name("Cash")
                .accountQualifier(AccountQualifier.ASSET).normalBalance(NormalBalance.DR)
                .isSummary(false).isPostable(true).displayOrder(0).build();
        source = JournalSource.builder().id(UUID.randomUUID()).code("MANUAL").name("Manual").build();
        category = JournalCategory.builder().id(UUID.randomUUID()).code("STD").name("Standard").build();

        lenient().when(dimensionValueRepository.findById(accountId)).thenReturn(Optional.of(account));
        lenient().when(accountingPeriodRepository.findById(periodId)).thenReturn(Optional.of(period));
        lenient().when(legalEntityRepository.findById(legalEntityId)).thenReturn(Optional.of(legalEntity));
    }

    private JournalHeader header(String journalNumber, LocalDate glDate) {
        return JournalHeader.builder()
                .id(UUID.randomUUID())
                .journalNumber(journalNumber)
                .legalEntity(legalEntity)
                .accountingPeriod(period)
                .journalSource(source)
                .journalCategory(category)
                .description("Test journal")
                .glDate(glDate)
                .accountingDate(glDate)
                .status(JournalStatus.POSTED)
                .build();
    }

    private JournalLine line(JournalHeader header, BigDecimal dr, BigDecimal cr) {
        return JournalLine.builder()
                .id(UUID.randomUUID())
                .journalHeader(header)
                .lineNumber(1)
                .accountCombination(java.util.Map.of())
                .naturalAccount(account)
                .debitAmount(dr)
                .creditAmount(cr)
                .gstApplicable(false)
                .tdsApplicable(false)
                .build();
    }

    @Test
    void testGetAccountLedger_entriesInDateOrder() {
        JournalHeader h1 = header("JN-002", LocalDate.of(2025, 4, 10));
        JournalHeader h2 = header("JN-001", LocalDate.of(2025, 4, 5));
        when(accountBalanceRepository.findByLegalEntityIdAndAccountingPeriodIdAndNaturalAccountId(
                legalEntityId, periodId, accountId)).thenReturn(List.of());
        when(journalLineRepository
                .findByNaturalAccountIdAndJournalHeader_LegalEntityIdAndJournalHeader_AccountingPeriodIdAndJournalHeader_Status(
                        accountId, legalEntityId, periodId, JournalStatus.POSTED))
                .thenReturn(new java.util.ArrayList<>(List.of(
                        line(h1, new BigDecimal("100.00"), BigDecimal.ZERO),
                        line(h2, new BigDecimal("50.00"), BigDecimal.ZERO))));

        AccountLedgerResponse response = service.getAccountLedger(legalEntityId, periodId, accountId);

        assertThat(response.entries()).hasSize(2);
        assertThat(response.entries().get(0).journalNumber()).isEqualTo("JN-001");
        assertThat(response.entries().get(1).journalNumber()).isEqualTo("JN-002");
    }

    @Test
    void testGetAccountLedger_runningBalanceAccumulatesCorrectly() {
        JournalHeader h1 = header("JN-001", LocalDate.of(2025, 4, 5));
        JournalHeader h2 = header("JN-002", LocalDate.of(2025, 4, 10));
        when(accountBalanceRepository.findByLegalEntityIdAndAccountingPeriodIdAndNaturalAccountId(
                legalEntityId, periodId, accountId)).thenReturn(List.of());
        when(journalLineRepository
                .findByNaturalAccountIdAndJournalHeader_LegalEntityIdAndJournalHeader_AccountingPeriodIdAndJournalHeader_Status(
                        accountId, legalEntityId, periodId, JournalStatus.POSTED))
                .thenReturn(new java.util.ArrayList<>(List.of(
                        line(h1, new BigDecimal("500.00"), BigDecimal.ZERO),
                        line(h2, BigDecimal.ZERO, new BigDecimal("200.00")))));

        AccountLedgerResponse response = service.getAccountLedger(legalEntityId, periodId, accountId);

        assertThat(response.entries().get(0).runningBalance()).isEqualByComparingTo("500.00");
        assertThat(response.entries().get(1).runningBalance()).isEqualByComparingTo("300.00");
    }

    @Test
    void testGetAccountLedger_openingBalanceFromAccountBalance() {
        AccountBalance balance = AccountBalance.builder()
                .id(UUID.randomUUID())
                .naturalAccount(account)
                .accountCombination(java.util.Map.of())
                .beginningBalance(new BigDecimal("1000.00"))
                .build();
        when(accountBalanceRepository.findByLegalEntityIdAndAccountingPeriodIdAndNaturalAccountId(
                legalEntityId, periodId, accountId)).thenReturn(List.of(balance));
        when(journalLineRepository
                .findByNaturalAccountIdAndJournalHeader_LegalEntityIdAndJournalHeader_AccountingPeriodIdAndJournalHeader_Status(
                        accountId, legalEntityId, periodId, JournalStatus.POSTED))
                .thenReturn(new java.util.ArrayList<>());

        AccountLedgerResponse response = service.getAccountLedger(legalEntityId, periodId, accountId);

        assertThat(response.openingBalance()).isEqualByComparingTo("1000.00");
    }

    @Test
    void testGetAccountLedger_closingBalance_openingPlusDrMinusCr() {
        AccountBalance balance = AccountBalance.builder()
                .id(UUID.randomUUID())
                .naturalAccount(account)
                .accountCombination(java.util.Map.of())
                .beginningBalance(new BigDecimal("200.00"))
                .build();
        JournalHeader h1 = header("JN-001", LocalDate.of(2025, 4, 5));
        when(accountBalanceRepository.findByLegalEntityIdAndAccountingPeriodIdAndNaturalAccountId(
                legalEntityId, periodId, accountId)).thenReturn(List.of(balance));
        when(journalLineRepository
                .findByNaturalAccountIdAndJournalHeader_LegalEntityIdAndJournalHeader_AccountingPeriodIdAndJournalHeader_Status(
                        accountId, legalEntityId, periodId, JournalStatus.POSTED))
                .thenReturn(new java.util.ArrayList<>(List.of(
                        line(h1, new BigDecimal("300.00"), new BigDecimal("50.00")))));

        AccountLedgerResponse response = service.getAccountLedger(legalEntityId, periodId, accountId);

        assertThat(response.closingBalance()).isEqualByComparingTo("450.00");
        assertThat(response.totalDebits()).isEqualByComparingTo("300.00");
        assertThat(response.totalCredits()).isEqualByComparingTo("50.00");
    }

    @Test
    void testGetAccountLedger_noEntries_returnsEmptyWithOpeningBalance() {
        AccountBalance balance = AccountBalance.builder()
                .id(UUID.randomUUID())
                .naturalAccount(account)
                .accountCombination(java.util.Map.of())
                .beginningBalance(new BigDecimal("750.00"))
                .build();
        when(accountBalanceRepository.findByLegalEntityIdAndAccountingPeriodIdAndNaturalAccountId(
                legalEntityId, periodId, accountId)).thenReturn(List.of(balance));
        when(journalLineRepository
                .findByNaturalAccountIdAndJournalHeader_LegalEntityIdAndJournalHeader_AccountingPeriodIdAndJournalHeader_Status(
                        accountId, legalEntityId, periodId, JournalStatus.POSTED))
                .thenReturn(new java.util.ArrayList<>());

        AccountLedgerResponse response = service.getAccountLedger(legalEntityId, periodId, accountId);

        assertThat(response.entries()).isEmpty();
        assertThat(response.openingBalance()).isEqualByComparingTo("750.00");
        assertThat(response.closingBalance()).isEqualByComparingTo("750.00");
        assertThat(response.entryCount()).isZero();
    }

    @Test
    void testGetAccountLedger_totalDebitsAndCreditsCorrect() {
        JournalHeader h1 = header("JN-001", LocalDate.of(2025, 4, 5));
        JournalHeader h2 = header("JN-002", LocalDate.of(2025, 4, 6));
        when(accountBalanceRepository.findByLegalEntityIdAndAccountingPeriodIdAndNaturalAccountId(
                legalEntityId, periodId, accountId)).thenReturn(List.of());
        when(journalLineRepository
                .findByNaturalAccountIdAndJournalHeader_LegalEntityIdAndJournalHeader_AccountingPeriodIdAndJournalHeader_Status(
                        accountId, legalEntityId, periodId, JournalStatus.POSTED))
                .thenReturn(new java.util.ArrayList<>(List.of(
                        line(h1, new BigDecimal("400.00"), BigDecimal.ZERO),
                        line(h2, BigDecimal.ZERO, new BigDecimal("150.00")))));

        AccountLedgerResponse response = service.getAccountLedger(legalEntityId, periodId, accountId);

        assertThat(response.totalDebits()).isEqualByComparingTo("400.00");
        assertThat(response.totalCredits()).isEqualByComparingTo("150.00");
    }
}
