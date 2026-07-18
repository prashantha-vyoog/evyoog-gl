package com.evyoog.gl.reporting.cashflow.service;

import com.evyoog.gl.common.exception.EvyoogException;
import com.evyoog.gl.dimension.domain.AccountQualifier;
import com.evyoog.gl.dimension.domain.DimensionValue;
import com.evyoog.gl.dimension.domain.NormalBalance;
import com.evyoog.gl.ledger.domain.FinanceMode;
import com.evyoog.gl.ledger.domain.Ledger;
import com.evyoog.gl.ledger.repository.LegalEntityLedgerRepository;
import com.evyoog.gl.posting.domain.AccountBalance;
import com.evyoog.gl.posting.repository.AccountBalanceRepository;
import com.evyoog.gl.reporting.cashflow.dto.CashFlowResponse;
import com.evyoog.gl.reporting.cashflow.dto.CashFlowSection;
import com.evyoog.gl.reporting.pnl.dto.ProfitAndLossResponse;
import com.evyoog.gl.reporting.pnl.service.ProfitAndLossService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashFlowServiceTest {

    @Mock private LegalEntityLedgerRepository legalEntityLedgerRepository;
    @Mock private AccountBalanceRepository accountBalanceRepository;
    @Mock private ProfitAndLossService profitAndLossService;

    private CashFlowService service;

    private UUID legalEntityId;
    private UUID periodId;
    private UUID ledgerId;

    @BeforeEach
    void setUp() {
        service = new CashFlowService(legalEntityLedgerRepository, accountBalanceRepository, profitAndLossService);

        legalEntityId = UUID.randomUUID();
        periodId = UUID.randomUUID();
        ledgerId = UUID.randomUUID();
    }

    private Ledger ledger(FinanceMode mode) {
        return Ledger.builder().id(ledgerId).code("LDG").name("Primary Ledger").financeMode(mode).build();
    }

    private ProfitAndLossResponse pl(BigDecimal netIncome) {
        return ProfitAndLossResponse.builder()
                .legalEntityId(legalEntityId)
                .legalEntityName("Orbinox Valves India Pvt Ltd")
                .legalEntityCode("LE1")
                .accountingPeriodId(periodId)
                .periodName("APR-2025")
                .fiscalYear("2025-26")
                .financeMode(FinanceMode.THICK.name())
                .generatedAt(LocalDate.now())
                .revenueItems(List.of())
                .totalRevenue(BigDecimal.ZERO)
                .expenseItems(List.of())
                .totalExpenses(BigDecimal.ZERO)
                .grossProfit(netIncome)
                .netIncome(netIncome)
                .isProfitable(netIncome.compareTo(BigDecimal.ZERO) > 0)
                .build();
    }

    private DimensionValue account(String code, String name, AccountQualifier qualifier) {
        return DimensionValue.builder().id(UUID.randomUUID()).code(code).name(name)
                .accountQualifier(qualifier)
                .normalBalance(qualifier == AccountQualifier.ASSET || qualifier == AccountQualifier.EXPENSE
                        ? NormalBalance.DR : NormalBalance.CR)
                .isSummary(false).isPostable(true).displayOrder(0).build();
    }

    private AccountBalance balance(DimensionValue account, BigDecimal beginning, BigDecimal ptdDr, BigDecimal ptdCr) {
        return AccountBalance.builder()
                .id(UUID.randomUUID())
                .naturalAccount(account)
                .accountCombination(java.util.Map.of())
                .beginningBalance(beginning)
                .periodToDateDr(ptdDr)
                .periodToDateCr(ptdCr)
                .build();
    }

    private void stubHappyPath(BigDecimal netIncome, List<AccountBalance> balances, FinanceMode mode) {
        when(legalEntityLedgerRepository.findPrimaryLedgerByLegalEntityId(legalEntityId))
                .thenReturn(Optional.of(ledger(mode)));
        lenient().when(profitAndLossService.generate(legalEntityId, periodId)).thenReturn(pl(netIncome));
        lenient().when(accountBalanceRepository.findByLegalEntityIdAndAccountingPeriodId(legalEntityId, periodId))
                .thenReturn(balances);
    }

    @Test
    void testGetCashFlow_thickMode_returnsCorrectSections() {
        DimensionValue cash = account("1100", "Cash", AccountQualifier.ASSET);
        stubHappyPath(new BigDecimal("5500000.00"),
                List.of(balance(cash, BigDecimal.ZERO, new BigDecimal("5500000.00"), BigDecimal.ZERO)),
                FinanceMode.THICK);

        CashFlowResponse response = service.getCashFlow(legalEntityId, periodId);

        assertThat(response.method()).isEqualTo("INDIRECT");
        assertThat(response.operatingActivities()).isNotNull();
        assertThat(response.investingActivities()).isNotNull();
        assertThat(response.financingActivities()).isNotNull();
    }

    @Test
    void testGetCashFlow_operatingSection_includesNetIncome() {
        DimensionValue cash = account("1100", "Cash", AccountQualifier.ASSET);
        stubHappyPath(new BigDecimal("5500000.00"),
                List.of(balance(cash, BigDecimal.ZERO, new BigDecimal("5500000.00"), BigDecimal.ZERO)),
                FinanceMode.THICK);

        CashFlowResponse response = service.getCashFlow(legalEntityId, periodId);

        CashFlowSection operating = response.operatingActivities();
        assertThat(operating.items().get(0).description()).isEqualTo("Net Income");
        assertThat(operating.items().get(0).amount()).isEqualByComparingTo("5500000.00");
    }

    @Test
    void testGetCashFlow_operatingSection_addBackDepreciation() {
        DimensionValue depreciation = account("5500", "Depreciation Expense", AccountQualifier.EXPENSE);
        stubHappyPath(new BigDecimal("-200000.00"),
                List.of(balance(depreciation, BigDecimal.ZERO, new BigDecimal("200000.00"), BigDecimal.ZERO)),
                FinanceMode.THICK);

        CashFlowResponse response = service.getCashFlow(legalEntityId, periodId);

        var adjustment = response.operatingActivities().items().stream()
                .filter(i -> i.itemType().equals("NON_CASH_ADJUSTMENT"))
                .findFirst().orElseThrow();
        assertThat(adjustment.description()).isEqualTo("Add: Depreciation Expense");
        assertThat(adjustment.amount()).isEqualByComparingTo("200000.00");
    }

    @Test
    void testGetCashFlow_operatingSection_workingCapitalChanges() {
        DimensionValue receivable = account("1300", "Accounts Receivable", AccountQualifier.ASSET);
        DimensionValue payable = account("2100", "Accounts Payable", AccountQualifier.LIABILITY);
        stubHappyPath(BigDecimal.ZERO,
                List.of(
                        balance(receivable, BigDecimal.ZERO, new BigDecimal("800000.00"), BigDecimal.ZERO),
                        balance(payable, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("3300000.00"))),
                FinanceMode.THICK);

        CashFlowResponse response = service.getCashFlow(legalEntityId, periodId);

        var items = response.operatingActivities().items();
        var arItem = items.stream().filter(i -> i.description().contains("Accounts Receivable")).findFirst().orElseThrow();
        var apItem = items.stream().filter(i -> i.description().contains("Accounts Payable")).findFirst().orElseThrow();

        assertThat(arItem.description()).startsWith("(Increase)");
        assertThat(arItem.amount()).isEqualByComparingTo("-800000.00");
        assertThat(apItem.description()).startsWith("Increase");
        assertThat(apItem.amount()).isEqualByComparingTo("3300000.00");
    }

    @Test
    void testGetCashFlow_investingSection_fixedAssetMovements() {
        DimensionValue plant = account("1700", "Plant and Machinery", AccountQualifier.ASSET);
        stubHappyPath(BigDecimal.ZERO,
                List.of(balance(plant, BigDecimal.ZERO, new BigDecimal("500000.00"), BigDecimal.ZERO)),
                FinanceMode.THICK);

        CashFlowResponse response = service.getCashFlow(legalEntityId, periodId);

        var item = response.investingActivities().items().get(0);
        assertThat(item.description()).isEqualTo("Purchase of Plant and Machinery");
        assertThat(item.amount()).isEqualByComparingTo("-500000.00");
        assertThat(response.investingActivities().totalAmount()).isEqualByComparingTo("-500000.00");
    }

    @Test
    void testGetCashFlow_financingSection_equityAndLoans() {
        DimensionValue equity = account("3100", "Share Capital", AccountQualifier.EQUITY);
        DimensionValue loan = account("2600", "Term Loan", AccountQualifier.LIABILITY);
        stubHappyPath(BigDecimal.ZERO,
                List.of(
                        balance(equity, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("20000000.00")),
                        balance(loan, BigDecimal.ZERO, new BigDecimal("100000.00"), BigDecimal.ZERO)),
                FinanceMode.THICK);

        CashFlowResponse response = service.getCashFlow(legalEntityId, periodId);

        var items = response.financingActivities().items();
        var equityItem = items.stream().filter(i -> i.description().contains("Share Capital")).findFirst().orElseThrow();
        var loanItem = items.stream().filter(i -> i.description().contains("Term Loan")).findFirst().orElseThrow();

        assertThat(equityItem.description()).isEqualTo("Proceeds from Share Capital");
        assertThat(equityItem.amount()).isEqualByComparingTo("20000000.00");
        assertThat(loanItem.description()).isEqualTo("Repayment of Term Loan");
        assertThat(loanItem.amount()).isEqualByComparingTo("-100000.00");
    }

    @Test
    void testGetCashFlow_netCashChange_closingEqualsOpeningPlusNetChange() {
        DimensionValue cash = account("1100", "Cash", AccountQualifier.ASSET);
        DimensionValue equity = account("3100", "Share Capital", AccountQualifier.EQUITY);
        stubHappyPath(BigDecimal.ZERO,
                List.of(
                        balance(cash, new BigDecimal("1000.00"), new BigDecimal("5000.00"), BigDecimal.ZERO),
                        balance(equity, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("5000.00"))),
                FinanceMode.THICK);

        CashFlowResponse response = service.getCashFlow(legalEntityId, periodId);

        assertThat(response.openingCashBalance()).isEqualByComparingTo("1000.00");
        assertThat(response.netCashChange()).isEqualByComparingTo("5000.00");
        assertThat(response.closingCashBalance())
                .isEqualByComparingTo(response.openingCashBalance().add(response.netCashChange()));
    }

    @Test
    void testGetCashFlow_cashAccounts_identifiedCorrectly() {
        DimensionValue cash = account("1100", "Cash", AccountQualifier.ASSET);
        DimensionValue receivable = account("1300", "Accounts Receivable", AccountQualifier.ASSET);
        stubHappyPath(BigDecimal.ZERO,
                List.of(
                        balance(cash, new BigDecimal("2000.00"), new BigDecimal("500.00"), BigDecimal.ZERO),
                        balance(receivable, BigDecimal.ZERO, new BigDecimal("800000.00"), BigDecimal.ZERO)),
                FinanceMode.THICK);

        CashFlowResponse response = service.getCashFlow(legalEntityId, periodId);

        // Cash itself is the balance being explained — it must never appear as a line item.
        boolean cashAppearsAsLineItem = response.operatingActivities().items().stream()
                .anyMatch(i -> i.description().contains("Cash"))
                || response.investingActivities().items().stream().anyMatch(i -> i.description().contains("Cash"))
                || response.financingActivities().items().stream().anyMatch(i -> i.description().contains("Cash"));
        assertThat(cashAppearsAsLineItem).isFalse();

        // Non-cash working capital (Accounts Receivable) is still classified into Operating.
        boolean receivableInOperating = response.operatingActivities().items().stream()
                .anyMatch(i -> i.description().contains("Accounts Receivable"));
        assertThat(receivableInOperating).isTrue();

        assertThat(response.openingCashBalance()).isEqualByComparingTo("2000.00");
    }

    @Test
    void testGetCashFlow_thinMode_throws422() {
        when(legalEntityLedgerRepository.findPrimaryLedgerByLegalEntityId(legalEntityId))
                .thenReturn(Optional.of(ledger(FinanceMode.THIN)));

        assertThatThrownBy(() -> service.getCashFlow(legalEntityId, periodId))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "THICK_MODE_ONLY");
    }

    @Test
    void testGetCashFlow_eventOnlyMode_throws422() {
        when(legalEntityLedgerRepository.findPrimaryLedgerByLegalEntityId(legalEntityId))
                .thenReturn(Optional.of(ledger(FinanceMode.EVENT_ONLY)));

        assertThatThrownBy(() -> service.getCashFlow(legalEntityId, periodId))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "THICK_MODE_ONLY");
    }
}
