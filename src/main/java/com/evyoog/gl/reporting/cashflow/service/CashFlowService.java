package com.evyoog.gl.reporting.cashflow.service;

import com.evyoog.gl.common.exception.EvyoogException;
import com.evyoog.gl.dimension.domain.AccountQualifier;
import com.evyoog.gl.dimension.domain.DimensionValue;
import com.evyoog.gl.ledger.domain.FinanceMode;
import com.evyoog.gl.ledger.domain.Ledger;
import com.evyoog.gl.ledger.repository.LegalEntityLedgerRepository;
import com.evyoog.gl.posting.domain.AccountBalance;
import com.evyoog.gl.posting.repository.AccountBalanceRepository;
import com.evyoog.gl.reporting.cashflow.dto.CashFlowLineItem;
import com.evyoog.gl.reporting.cashflow.dto.CashFlowResponse;
import com.evyoog.gl.reporting.cashflow.dto.CashFlowSection;
import com.evyoog.gl.reporting.pnl.dto.ProfitAndLossResponse;
import com.evyoog.gl.reporting.pnl.service.ProfitAndLossService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Indirect-method Cash Flow Statement. Classification of Balance Sheet accounts into
 * Operating/Investing/Financing is a pragmatic Phase 1 approach driven by natural account
 * code ranges (1100-1299 cash, 1300-1699 current assets, 1700-1899 fixed assets,
 * 2100-2499 current liabilities, 2500+ long-term liabilities). Phase 2 should replace this
 * with dedicated account classification metadata rather than code ranges.
 */
@Service
@RequiredArgsConstructor
public class CashFlowService {

    private static final Pattern LEADING_DIGITS = Pattern.compile("^(\\d+)");

    private final LegalEntityLedgerRepository legalEntityLedgerRepository;
    private final AccountBalanceRepository accountBalanceRepository;
    private final ProfitAndLossService profitAndLossService;

    public CashFlowResponse getCashFlow(UUID legalEntityId, UUID periodId) {

        Ledger ledger = legalEntityLedgerRepository
                .findPrimaryLedgerByLegalEntityId(legalEntityId)
                .orElseThrow(() -> new EvyoogException("NO_PRIMARY_LEDGER",
                        "No primary Ledger found.", HttpStatus.NOT_FOUND));

        if (ledger.getFinanceMode() != FinanceMode.THICK) {
            throw new EvyoogException("THICK_MODE_ONLY",
                    "Cash Flow Statement is only available for THICK finance mode Ledgers. "
                            + "Current mode: " + ledger.getFinanceMode(),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // Reuse P&L for Net Income and the response header fields (legal entity/period/fiscal year).
        ProfitAndLossResponse pl = profitAndLossService.generate(legalEntityId, periodId);

        List<AccountBalance> balances = accountBalanceRepository
                .findByLegalEntityIdAndAccountingPeriodId(legalEntityId, periodId);

        List<AccountAggregate> aggregates = aggregateByAccount(balances);

        CashFlowSection operating = buildOperatingSection(pl.netIncome(), aggregates);
        CashFlowSection investing = buildInvestingSection(aggregates);
        CashFlowSection financing = buildFinancingSection(aggregates);

        BigDecimal netCashChange = operating.totalAmount()
                .add(investing.totalAmount())
                .add(financing.totalAmount());

        BigDecimal openingCashBalance = aggregates.stream()
                .filter(this::isCash)
                .map(AccountAggregate::beginningBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal closingCashBalance = openingCashBalance.add(netCashChange);

        return CashFlowResponse.builder()
                .legalEntityId(pl.legalEntityId())
                .legalEntityName(pl.legalEntityName())
                .legalEntityCode(pl.legalEntityCode())
                .accountingPeriodId(pl.accountingPeriodId())
                .periodName(pl.periodName())
                .fiscalYear(pl.fiscalYear())
                .financeMode(pl.financeMode())
                .method("INDIRECT")
                .generatedAt(LocalDate.now())
                .operatingActivities(operating)
                .investingActivities(investing)
                .financingActivities(financing)
                .netCashChange(netCashChange)
                .openingCashBalance(openingCashBalance)
                .closingCashBalance(closingCashBalance)
                .isPositiveCashFlow(netCashChange.compareTo(BigDecimal.ZERO) > 0)
                .build();
    }

    private CashFlowSection buildOperatingSection(BigDecimal netIncome, List<AccountAggregate> aggregates) {
        List<CashFlowLineItem> items = new ArrayList<>();
        items.add(CashFlowLineItem.of("Net Income", netIncome, "NET_INCOME"));

        aggregates.stream()
                .filter(this::isNonCashExpense)
                .sorted(Comparator.comparing(AccountAggregate::code))
                .forEach(a -> items.add(CashFlowLineItem.of(
                        "Add: " + a.name(), a.expenseMovement(), "NON_CASH_ADJUSTMENT")));

        aggregates.stream()
                .filter(this::isWorkingCapital)
                .sorted(Comparator.comparing(AccountAggregate::code))
                .forEach(a -> {
                    BigDecimal cashImpact = a.cashImpact();
                    boolean increased = cashImpact.compareTo(BigDecimal.ZERO) < 0;
                    String label = a.qualifier() == AccountQualifier.ASSET
                            ? (increased ? "(Increase) in " + a.name() : "Decrease in " + a.name())
                            : (increased ? "(Decrease) in " + a.name() : "Increase in " + a.name());
                    items.add(CashFlowLineItem.of(label, cashImpact, "WORKING_CAPITAL"));
                });

        return CashFlowSection.builder()
                .sectionCode("OPERATING")
                .sectionName("Operating Activities")
                .items(items)
                .totalAmount(sumAmounts(items))
                .build();
    }

    private CashFlowSection buildInvestingSection(List<AccountAggregate> aggregates) {
        List<CashFlowLineItem> items = aggregates.stream()
                .filter(this::isFixedAsset)
                .sorted(Comparator.comparing(AccountAggregate::code))
                .map(a -> {
                    BigDecimal cashImpact = a.cashImpact();
                    boolean isPurchase = cashImpact.compareTo(BigDecimal.ZERO) < 0;
                    String label = (isPurchase ? "Purchase of " : "Disposal of ") + a.name();
                    String itemType = isPurchase ? "ASSET_PURCHASE" : "ASSET_DISPOSAL";
                    return CashFlowLineItem.of(label, cashImpact, itemType);
                })
                .collect(Collectors.toList());

        return CashFlowSection.builder()
                .sectionCode("INVESTING")
                .sectionName("Investing Activities")
                .items(items)
                .totalAmount(sumAmounts(items))
                .build();
    }

    private CashFlowSection buildFinancingSection(List<AccountAggregate> aggregates) {
        List<CashFlowLineItem> items = aggregates.stream()
                .filter(this::isFinancing)
                .sorted(Comparator.comparing(AccountAggregate::code))
                .map(a -> {
                    BigDecimal cashImpact = a.cashImpact();
                    boolean isInflow = cashImpact.compareTo(BigDecimal.ZERO) >= 0;
                    String prefix = a.qualifier() == AccountQualifier.EQUITY
                            ? (isInflow ? "Proceeds from " : "Return of ")
                            : (isInflow ? "Proceeds from " : "Repayment of ");
                    String itemType = isInflow ? "FINANCING_INFLOW" : "FINANCING_OUTFLOW";
                    return CashFlowLineItem.of(prefix + a.name(), cashImpact, itemType);
                })
                .collect(Collectors.toList());

        return CashFlowSection.builder()
                .sectionCode("FINANCING")
                .sectionName("Financing Activities")
                .items(items)
                .totalAmount(sumAmounts(items))
                .build();
    }

    private BigDecimal sumAmounts(List<CashFlowLineItem> items) {
        return items.stream()
                .map(CashFlowLineItem::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isCash(AccountAggregate a) {
        return a.qualifier() == AccountQualifier.ASSET && codeInRange(a.code(), 1100, 1299);
    }

    private boolean isWorkingCapital(AccountAggregate a) {
        return (a.qualifier() == AccountQualifier.ASSET && codeInRange(a.code(), 1300, 1699))
                || (a.qualifier() == AccountQualifier.LIABILITY && codeInRange(a.code(), 2100, 2499));
    }

    private boolean isFixedAsset(AccountAggregate a) {
        return a.qualifier() == AccountQualifier.ASSET && codeInRange(a.code(), 1700, 1899);
    }

    private boolean isFinancing(AccountAggregate a) {
        return a.qualifier() == AccountQualifier.EQUITY
                || (a.qualifier() == AccountQualifier.LIABILITY && codeAtOrAbove(a.code(), 2500));
    }

    private boolean isNonCashExpense(AccountAggregate a) {
        return a.qualifier() == AccountQualifier.EXPENSE
                && ("5500".equals(a.code())
                        || a.name().toLowerCase().contains("depreciation")
                        || a.name().toLowerCase().contains("amortisation")
                        || a.name().toLowerCase().contains("amortization"));
    }

    private boolean codeInRange(String code, int min, int max) {
        Integer value = leadingNumericCode(code);
        return value != null && value >= min && value <= max;
    }

    private boolean codeAtOrAbove(String code, int min) {
        Integer value = leadingNumericCode(code);
        return value != null && value >= min;
    }

    private Integer leadingNumericCode(String code) {
        if (code == null) {
            return null;
        }
        Matcher matcher = LEADING_DIGITS.matcher(code);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1).substring(0, Math.min(4, matcher.group(1).length())));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<AccountAggregate> aggregateByAccount(List<AccountBalance> balances) {
        Map<UUID, List<AccountBalance>> byAccountId = balances.stream()
                .collect(Collectors.groupingBy(ab -> ab.getNaturalAccount().getId()));

        return byAccountId.values().stream()
                .map(group -> {
                    DimensionValue account = group.get(0).getNaturalAccount();
                    BigDecimal beginning = group.stream().map(AccountBalance::getBeginningBalance)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal ptdDr = group.stream().map(AccountBalance::getPeriodToDateDr)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal ptdCr = group.stream().map(AccountBalance::getPeriodToDateCr)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new AccountAggregate(account.getCode(), account.getName(),
                            account.getAccountQualifier(), beginning, ptdDr, ptdCr);
                })
                .collect(Collectors.toList());
    }

    private record AccountAggregate(
            String code,
            String name,
            AccountQualifier qualifier,
            BigDecimal beginningBalance,
            BigDecimal periodToDateDr,
            BigDecimal periodToDateCr) {

        // Increase in a DR-normal (asset) account consumes cash; increase in a CR-normal
        // (liability/equity) account is a source of cash. periodToDateCr - periodToDateDr
        // yields the correct signed cash impact for both without branching on qualifier.
        BigDecimal cashImpact() {
            return periodToDateCr.subtract(periodToDateDr);
        }

        // Depreciation/amortisation is an expense (DR-normal) that reduced Net Income but
        // consumed no cash, so it is added back at its raw expense movement.
        BigDecimal expenseMovement() {
            return periodToDateDr.subtract(periodToDateCr);
        }
    }
}
