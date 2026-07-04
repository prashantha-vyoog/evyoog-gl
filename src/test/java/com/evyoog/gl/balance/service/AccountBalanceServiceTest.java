package com.evyoog.gl.balance.service;

import com.evyoog.gl.balance.dto.AccountBalanceResponse;
import com.evyoog.gl.balance.dto.AccountBalanceSummaryResponse;
import com.evyoog.gl.balance.dto.CarryForwardResponse;
import com.evyoog.gl.balance.mapper.AccountBalanceMapperImpl;
import com.evyoog.gl.common.audit.service.AuditService;
import com.evyoog.gl.common.exception.EvyoogException;
import com.evyoog.gl.dimension.domain.AccountQualifier;
import com.evyoog.gl.dimension.domain.DimensionValue;
import com.evyoog.gl.enterprise.domain.LegalEntity;
import com.evyoog.gl.ledger.domain.Ledger;
import com.evyoog.gl.period.domain.AccountingPeriod;
import com.evyoog.gl.period.repository.AccountingPeriodRepository;
import com.evyoog.gl.periodstatus.domain.PeriodStatus;
import com.evyoog.gl.periodstatus.domain.PeriodStatusEnum;
import com.evyoog.gl.periodstatus.repository.PeriodStatusRepository;
import com.evyoog.gl.posting.domain.AccountBalance;
import com.evyoog.gl.posting.repository.AccountBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountBalanceServiceTest {

    @Mock private AccountBalanceRepository accountBalanceRepository;
    @Mock private AccountingPeriodRepository accountingPeriodRepository;
    @Mock private PeriodStatusRepository periodStatusRepository;
    @Mock private AuditService auditService;

    private AccountBalanceService service;

    private UUID legalEntityId;
    private UUID fromPeriodId;
    private UUID toPeriodId;

    private LegalEntity legalEntity;
    private Ledger ledger;
    private AccountingPeriod fromPeriod;
    private AccountingPeriod toPeriod;

    @BeforeEach
    void setUp() {
        service = new AccountBalanceService(accountBalanceRepository, accountingPeriodRepository,
                periodStatusRepository, new AccountBalanceMapperImpl(), auditService);

        legalEntityId = UUID.randomUUID();
        fromPeriodId = UUID.randomUUID();
        toPeriodId = UUID.randomUUID();

        legalEntity = LegalEntity.builder().id(legalEntityId).code("LE1").name("Legal Entity 1").build();
        ledger = Ledger.builder().id(UUID.randomUUID()).code("LDG").name("Primary Ledger").build();
        fromPeriod = AccountingPeriod.builder().id(fromPeriodId).name("MAR-2026").fiscalYear("2025-26").build();
        toPeriod = AccountingPeriod.builder().id(toPeriodId).name("APR-2026").fiscalYear("2026-27").build();
    }

    private DimensionValue account(String code, AccountQualifier qualifier) {
        return DimensionValue.builder().id(UUID.randomUUID()).code(code).name(code)
                .accountQualifier(qualifier).isPostable(true).isSummary(false).build();
    }

    private AccountBalance balance(AccountingPeriod period, DimensionValue account,
                                    BigDecimal beginning, BigDecimal ptdDr, BigDecimal ptdCr) {
        return AccountBalance.builder()
                .id(UUID.randomUUID())
                .ledger(ledger)
                .legalEntity(legalEntity)
                .accountingPeriod(period)
                .naturalAccount(account)
                .accountCombination(java.util.Map.of())
                .beginningBalance(beginning)
                .periodToDateDr(ptdDr)
                .periodToDateCr(ptdCr)
                .yearToDateDr(ptdDr)
                .yearToDateCr(ptdCr)
                .build();
    }

    @Test
    void testGetBalances_filterByLegalEntityAndPeriod() {
        DimensionValue cash = account("1000", AccountQualifier.ASSET);
        AccountBalance ab = balance(fromPeriod, cash, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ZERO);
        when(accountBalanceRepository.findByLegalEntityIdAndAccountingPeriodId(legalEntityId, fromPeriodId))
                .thenReturn(List.of(ab));

        List<AccountBalanceResponse> result = service.getBalances(legalEntityId, fromPeriodId, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).accountCode()).isEqualTo("1000");
        assertThat(result.get(0).legalEntityId()).isEqualTo(legalEntityId);
    }

    @Test
    void testGetSummary_correctTotalsByQualifier() {
        DimensionValue asset = account("1000", AccountQualifier.ASSET);
        DimensionValue liability = account("2000", AccountQualifier.LIABILITY);
        DimensionValue equity = account("3000", AccountQualifier.EQUITY);
        DimensionValue revenue = account("4000", AccountQualifier.REVENUE);
        DimensionValue expense = account("5000", AccountQualifier.EXPENSE);

        List<AccountBalance> balances = List.of(
                balance(fromPeriod, asset, BigDecimal.ZERO, new BigDecimal("1000"), BigDecimal.ZERO),
                balance(fromPeriod, liability, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("400")),
                balance(fromPeriod, equity, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("600")),
                balance(fromPeriod, revenue, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("500")),
                balance(fromPeriod, expense, BigDecimal.ZERO, new BigDecimal("200"), BigDecimal.ZERO));

        when(accountBalanceRepository.findByLegalEntityIdAndAccountingPeriodId(legalEntityId, fromPeriodId))
                .thenReturn(balances);
        when(accountingPeriodRepository.findById(fromPeriodId)).thenReturn(Optional.of(fromPeriod));

        AccountBalanceSummaryResponse summary = service.getSummary(legalEntityId, fromPeriodId);

        assertThat(summary.totalAssets()).isEqualByComparingTo("1000");
        assertThat(summary.totalLiabilities()).isEqualByComparingTo("-400");
        assertThat(summary.totalEquity()).isEqualByComparingTo("-600");
        assertThat(summary.totalRevenue()).isEqualByComparingTo("-500");
        assertThat(summary.totalExpenses()).isEqualByComparingTo("200");
    }

    @Test
    void testGetSummary_netIncome_revenueMinusExpenses() {
        DimensionValue revenue = account("4000", AccountQualifier.REVENUE);
        DimensionValue expense = account("5000", AccountQualifier.EXPENSE);

        List<AccountBalance> balances = List.of(
                balance(fromPeriod, revenue, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1000")),
                balance(fromPeriod, expense, BigDecimal.ZERO, new BigDecimal("300"), BigDecimal.ZERO));

        when(accountBalanceRepository.findByLegalEntityIdAndAccountingPeriodId(legalEntityId, fromPeriodId))
                .thenReturn(balances);
        when(accountingPeriodRepository.findById(fromPeriodId)).thenReturn(Optional.of(fromPeriod));

        AccountBalanceSummaryResponse summary = service.getSummary(legalEntityId, fromPeriodId);

        // netIncome = totalRevenue - totalExpenses = (-1000) - (300) = -1300
        assertThat(summary.netIncome()).isEqualByComparingTo(summary.totalRevenue().subtract(summary.totalExpenses()));
    }

    @Test
    void testCarryForward_balanceSheetAccountsOnly() {
        DimensionValue asset = account("1000", AccountQualifier.ASSET);
        DimensionValue liability = account("2000", AccountQualifier.LIABILITY);

        AccountBalance assetBalance = balance(fromPeriod, asset, BigDecimal.ZERO, new BigDecimal("500"), BigDecimal.ZERO);
        AccountBalance liabilityBalance = balance(fromPeriod, liability, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("500"));

        PeriodStatus closed = PeriodStatus.builder().status(PeriodStatusEnum.CLOSED).build();
        when(periodStatusRepository.findByLegalEntityIdAndAccountingPeriodId(legalEntityId, fromPeriodId))
                .thenReturn(Optional.of(closed));
        when(accountBalanceRepository.existsByLegalEntityIdAndAccountingPeriodId(legalEntityId, toPeriodId))
                .thenReturn(false);
        when(accountingPeriodRepository.findById(fromPeriodId)).thenReturn(Optional.of(fromPeriod));
        when(accountingPeriodRepository.findById(toPeriodId)).thenReturn(Optional.of(toPeriod));
        when(accountBalanceRepository.findByLegalEntityIdAndAccountingPeriodId(legalEntityId, fromPeriodId))
                .thenReturn(List.of(assetBalance, liabilityBalance));
        lenient().when(accountBalanceRepository.save(any(AccountBalance.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CarryForwardResponse response = service.carryForward(legalEntityId, fromPeriodId, toPeriodId, "tester");

        assertThat(response.balancesCarriedForward()).isEqualTo(2);
        assertThat(response.accountsCarriedForward()).containsExactlyInAnyOrder("1000", "2000");
        verify(accountBalanceRepository, times(2)).save(any(AccountBalance.class));
    }

    @Test
    void testCarryForward_pnlAccountsNotCarried() {
        DimensionValue asset = account("1000", AccountQualifier.ASSET);
        DimensionValue revenue = account("4000", AccountQualifier.REVENUE);
        DimensionValue expense = account("5000", AccountQualifier.EXPENSE);

        AccountBalance assetBalance = balance(fromPeriod, asset, BigDecimal.ZERO, new BigDecimal("500"), BigDecimal.ZERO);
        AccountBalance revenueBalance = balance(fromPeriod, revenue, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1000"));
        AccountBalance expenseBalance = balance(fromPeriod, expense, BigDecimal.ZERO, new BigDecimal("300"), BigDecimal.ZERO);

        PeriodStatus closed = PeriodStatus.builder().status(PeriodStatusEnum.CLOSED).build();
        when(periodStatusRepository.findByLegalEntityIdAndAccountingPeriodId(legalEntityId, fromPeriodId))
                .thenReturn(Optional.of(closed));
        when(accountBalanceRepository.existsByLegalEntityIdAndAccountingPeriodId(legalEntityId, toPeriodId))
                .thenReturn(false);
        when(accountingPeriodRepository.findById(fromPeriodId)).thenReturn(Optional.of(fromPeriod));
        when(accountingPeriodRepository.findById(toPeriodId)).thenReturn(Optional.of(toPeriod));
        when(accountBalanceRepository.findByLegalEntityIdAndAccountingPeriodId(legalEntityId, fromPeriodId))
                .thenReturn(List.of(assetBalance, revenueBalance, expenseBalance));
        lenient().when(accountBalanceRepository.save(any(AccountBalance.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CarryForwardResponse response = service.carryForward(legalEntityId, fromPeriodId, toPeriodId, "tester");

        assertThat(response.balancesCarriedForward()).isEqualTo(1);
        assertThat(response.accountsCarriedForward()).containsExactly("1000");
        verify(accountBalanceRepository, times(1)).save(any(AccountBalance.class));
    }

    @Test
    void testCarryForward_fromPeriodNotClosed_throws409() {
        PeriodStatus open = PeriodStatus.builder().status(PeriodStatusEnum.OPEN).build();
        when(periodStatusRepository.findByLegalEntityIdAndAccountingPeriodId(legalEntityId, fromPeriodId))
                .thenReturn(Optional.of(open));

        assertThatThrownBy(() -> service.carryForward(legalEntityId, fromPeriodId, toPeriodId, "tester"))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "FROM_PERIOD_NOT_CLOSED");

        verify(accountBalanceRepository, never()).save(any());
    }

    @Test
    void testCarryForward_alreadyDone_throws409() {
        PeriodStatus closed = PeriodStatus.builder().status(PeriodStatusEnum.CLOSED).build();
        when(periodStatusRepository.findByLegalEntityIdAndAccountingPeriodId(legalEntityId, fromPeriodId))
                .thenReturn(Optional.of(closed));
        when(accountBalanceRepository.existsByLegalEntityIdAndAccountingPeriodId(legalEntityId, toPeriodId))
                .thenReturn(true);

        assertThatThrownBy(() -> service.carryForward(legalEntityId, fromPeriodId, toPeriodId, "tester"))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "CARRY_FORWARD_ALREADY_DONE");

        verify(accountBalanceRepository, never()).save(any());
    }

    @Test
    void testEndingBalance_calculatedCorrectly() {
        DimensionValue cash = account("1000", AccountQualifier.ASSET);
        AccountBalance ab = balance(fromPeriod, cash, new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("20"));

        when(accountBalanceRepository.findByLegalEntityIdAndAccountingPeriodId(legalEntityId, fromPeriodId))
                .thenReturn(List.of(ab));

        List<AccountBalanceResponse> result = service.getBalances(legalEntityId, fromPeriodId, null);

        // endingBalance = beginningBalance + periodToDateDr - periodToDateCr = 100 + 50 - 20 = 130
        assertThat(result.get(0).endingBalance()).isEqualByComparingTo("130");
    }
}
